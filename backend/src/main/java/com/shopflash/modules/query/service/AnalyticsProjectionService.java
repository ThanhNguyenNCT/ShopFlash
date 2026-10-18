package com.shopflash.modules.query.service;

import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.COUNTED_STATUS;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.LINE_INDEX;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.LINE_PREFIX;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.META;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.ORDER_INDEX;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.ORDER_PREFIX;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.shopflash.modules.query.dto.response.AnalyticsStatusResponse;
import com.shopflash.modules.query.dto.response.RebuildResponse;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.repository.redis.RedisCommandRunner;
import com.shopflash.modules.query.repository.redis.RedisReplies;

import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Dựng read model Redis (order:analytics:*, order:line:*) từ Oracle. Đọc Oracle theo luồng (fetch size 2000,
 * sắp theo order_id) và ghi Redis bằng pipeline mỗi lô ~1000 đơn, nên bộ nhớ chỉ giữ một lô.
 */
@Service
public class AnalyticsProjectionService implements AnalyticsProjection {

    static final int ORDERS_PER_PIPELINE = 1000;
    static final int FETCH_SIZE = 2000;
    static final int SCAN_COUNT = 5000;
    static final int WRITER_THREADS = 2;

    private static final Logger log = LoggerFactory.getLogger(AnalyticsProjectionService.class);

    /** Mọi trạng thái đơn; item lấy tên sản phẩm và danh mục HIỆN TẠI (danh mục của sản phẩm). */
    static final String STREAM_SQL = """
            SELECT o.order_id, o.user_id, u.full_name, u.email, o.status, o.order_type, o.total_amount, o.created_at,
                   oi.order_item_id, oi.product_id, p.name AS product_name, p.category_id, c.name AS category_name,
                   oi.quantity, oi.line_total
              FROM orders o
              JOIN users u ON u.user_id = o.user_id
              LEFT JOIN order_items oi ON oi.order_id = o.order_id
              LEFT JOIN products p ON p.product_id = oi.product_id
              LEFT JOIN categories c ON c.category_id = p.category_id
             ORDER BY o.order_id, oi.order_item_id""";

    static final String COUNT_ORDERS_SQL =
            "SELECT COUNT(*) FROM orders WHERE status IN ('CONFIRMED', 'COMPLETED')";
    static final String COUNT_ITEMS_SQL = """
            SELECT COUNT(*) FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
             WHERE o.status IN ('CONFIRMED', 'COMPLETED')""";

    private static final ProtocolCommand JSON_SET = RedisCommandRunner.command("JSON.SET");

    private final JdbcTemplate jdbc;
    private final RedisCommandRunner redis;
    private final ReentrantLock rebuildLock = new ReentrantLock();

    public AnalyticsProjectionService(DataSource dataSource, RedisCommandRunner redis) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(FETCH_SIZE);
        this.redis = redis;
    }

    @Override
    public AnalyticsStatusResponse status() {
        long countedOrders;
        long orderItems;
        try {
            countedOrders = jdbc.queryForObject(COUNT_ORDERS_SQL, Long.class);
            orderItems = jdbc.queryForObject(COUNT_ITEMS_SQL, Long.class);
        } catch (DataAccessException e) {
            throw new ApiException(ErrorCode.ORACLE_ERROR, "Lỗi Oracle: " + e.getMostSpecificCause().getMessage(),
                    null, e);
        }

        List<Object> replies = redis.pipeline(List.of(
                new String[] {"FT.SEARCH", ORDER_INDEX, COUNTED_STATUS, "TIMEOUT", "0", "LIMIT", "0", "0"},
                new String[] {"FT.SEARCH", LINE_INDEX, COUNTED_STATUS, "TIMEOUT", "0", "LIMIT", "0", "0"},
                new String[] {"FT.INFO", ORDER_INDEX},
                new String[] {"FT.INFO", LINE_INDEX},
                new String[] {"HGETALL", META}));
        long orderDocs;
        long lineDocs;
        boolean indexing;
        try {
            orderDocs = RedisReplies.asLong(RedisReplies.list(replies.get(0)).get(0));
            lineDocs = RedisReplies.asLong(RedisReplies.list(replies.get(1)).get(0));
            indexing = isIndexing(replies.get(2)) || isIndexing(replies.get(3));
        } catch (RuntimeException e) {
            throw RedisCommandRunner.translate(e);
        }
        Map<String, String> meta = RedisReplies.pairs(replies.get(4));
        OffsetDateTime lastRebuildAt = meta.containsKey("lastRebuildAt")
                ? OffsetDateTime.parse(meta.get("lastRebuildAt")) : null;
        Double lastRebuildMs = meta.containsKey("elapsedMs") ? Double.valueOf(meta.get("elapsedMs")) : null;

        return new AnalyticsStatusResponse(
                new AnalyticsStatusResponse.OracleSide(countedOrders, orderItems),
                new AnalyticsStatusResponse.RedisSide(orderDocs, lineDocs, indexing, lastRebuildAt, lastRebuildMs),
                countedOrders == orderDocs && orderItems == lineDocs);
    }

    private static boolean isIndexing(Object info) {
        Object v = RedisReplies.field(info, "indexing");
        if (v == null) {
            return false;
        }
        String s = RedisReplies.str(v);
        return !("0".equals(s) || "false".equalsIgnoreCase(s));
    }

    @Override
    public RebuildResponse rebuild() {
        rebuildLock.lock();
        try {
            long start = System.nanoTime();
            // Index phải có sẵn (init.lua); thiếu thì báo REDIS_NOT_BUILT trước khi xóa gì.
            redis.pipeline(List.of(new String[] {"FT.INFO", ORDER_INDEX}, new String[] {"FT.INFO", LINE_INDEX}));
            Loader loader;
            long writeNanos;
            try (Writer writer = new Writer()) {
                loader = new Loader(writer);
                try {
                    jdbc.query(STREAM_SQL, loader::accept);
                } catch (DataAccessException e) {
                    throw new ApiException(ErrorCode.ORACLE_ERROR,
                            "Lỗi Oracle khi đọc đơn hàng: " + e.getMostSpecificCause().getMessage(), null, e);
                }
                loader.finish();
                writeNanos = writer.writeNanos.get();
            }
            long loaded = System.nanoTime();
            // Key cũ không còn trong Oracle (đơn/dòng hàng đã bị xóa) thì UNLINK; key còn lại đã bị JSON.SET ghi đè.
            long stale = deleteStale(ORDER_PREFIX, loader.orderIds.sorted())
                    + deleteStale(LINE_PREFIX, loader.lineIds.sorted());

            log.info("Analytics rebuild: load {} ms (ghi Redis cộng dồn các luồng {} ms), xóa {} key cũ {} ms, "
                            + "{} đơn, {} dòng hàng",
                    (loaded - start) / 1_000_000, writeNanos / 1_000_000, stale,
                    (System.nanoTime() - loaded) / 1_000_000, loader.orders, loader.lines);
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            elapsedMs = Math.round(elapsedMs * 10) / 10.0;
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("lastRebuildAt", OffsetDateTime.now(ReportQuery.ZONE).withNano(0).toString());
            meta.put("orders", Long.toString(loader.orders));
            meta.put("lines", Long.toString(loader.lines));
            meta.put("elapsedMs", Double.toString(elapsedMs));
            try {
                redis.client().del(META);
                redis.client().hset(META, meta);
            } catch (RuntimeException e) {
                throw RedisCommandRunner.translate(e);
            }
            return new RebuildResponse(loader.orders, loader.lines, elapsedMs);
        } finally {
            rebuildLock.unlock();
        }
    }

    /**
     * SCAN (không dùng KEYS) các key có prefix, UNLINK theo lô những key có id không nằm trong tập vừa nạp.
     * Kết quả giống "xóa hết rồi nạp lại" nhưng không phải xóa rồi index lại ~350k document còn nguyên,
     * và báo cáo không thấy read model rỗng trong lúc rebuild.
     */
    private long deleteStale(String prefix, long[] keptIds) {
        ScanParams params = new ScanParams().match(prefix + "*").count(SCAN_COUNT);
        String cursor = ScanParams.SCAN_POINTER_START;
        long removed = 0;
        try {
            do {
                ScanResult<String> page = redis.client().scan(cursor, params);
                List<String> stale = new ArrayList<>();
                for (String key : page.getResult()) {
                    if (!isKept(key.substring(prefix.length()), keptIds)) {
                        stale.add(key);
                    }
                }
                if (!stale.isEmpty()) {
                    removed += redis.client().unlink(stale.toArray(String[]::new));
                }
                cursor = page.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        } catch (RuntimeException e) {
            throw RedisCommandRunner.translate(e);
        }
        return removed;
    }

    private static boolean isKept(String suffix, long[] keptIds) {
        try {
            return Arrays.binarySearch(keptIds, Long.parseLong(suffix)) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Danh sách long tăng dần kích thước, không boxing (350k id ~ 3 MB). */
    private static final class LongList {
        private long[] values = new long[1024];
        private int size;

        void add(long v) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = v;
        }

        long[] sorted() {
            long[] out = Arrays.copyOf(values, size);
            Arrays.sort(out);
            return out;
        }
    }

    /** Gom các dòng của cùng một order_id thành document, đầy lô thì đẩy qua pipeline. */
    private final class Loader {
        private final Writer writer;
        private final LongList orderIds = new LongList();
        private final LongList lineIds = new LongList();
        private final List<String[]> batch = new ArrayList<>(ORDERS_PER_PIPELINE * 4);
        private int ordersInBatch;
        private long orders;
        private long lines;

        private long currentOrderId = -1;
        private StringBuilder orderHead;
        private StringBuilder items;
        private boolean firstItem;
        private String lineHead;

        Loader(Writer writer) {
            this.writer = writer;
        }

        void accept(ResultSet rs) throws SQLException {
            long orderId = rs.getLong(1);
            if (orderId != currentOrderId) {
                closeOrder();
                startOrder(rs, orderId);
            }
            long itemId = rs.getLong(9);
            if (rs.wasNull()) {
                return;
            }
            long productId = rs.getLong(10);
            String productName = rs.getString(11);
            long categoryId = rs.getLong(12);
            String categoryName = rs.getString(13);
            long quantity = rs.getLong(14);
            String lineTotal = plain(rs.getBigDecimal(15));

            if (!firstItem) {
                items.append(',');
            }
            firstItem = false;
            items.append("{\"productId\":").append(productId)
                    .append(",\"categoryId\":").append(categoryId)
                    .append(",\"quantity\":").append(quantity)
                    .append(",\"lineTotal\":").append(lineTotal).append('}');

            StringBuilder line = new StringBuilder(256).append(lineHead)
                    .append(",\"productId\":").append(productId)
                    .append(",\"productName\":");
            appendString(line, productName);
            line.append(",\"categoryId\":").append(categoryId).append(",\"categoryName\":");
            appendString(line, categoryName);
            line.append(",\"quantity\":").append(quantity)
                    .append(",\"lineTotal\":").append(lineTotal).append('}');
            batch.add(new String[] {LINE_PREFIX + itemId, "$", line.toString()});
            lineIds.add(itemId);
            lines++;
        }

        private void startOrder(ResultSet rs, long orderId) throws SQLException {
            currentOrderId = orderId;
            long userId = rs.getLong(2);
            String status = rs.getString(5);
            String orderType = rs.getString(6);
            long createdAt = rs.getObject(8, OffsetDateTime.class).toEpochSecond();

            orderHead = new StringBuilder(256).append("{\"orderId\":").append(orderId)
                    .append(",\"userId\":").append(userId).append(",\"userName\":");
            appendString(orderHead, rs.getString(3));
            orderHead.append(",\"userEmail\":");
            appendString(orderHead, rs.getString(4));
            orderHead.append(",\"status\":");
            appendString(orderHead, status);
            orderHead.append(",\"orderType\":");
            appendString(orderHead, orderType);
            orderHead.append(",\"totalAmount\":").append(plain(rs.getBigDecimal(7)))
                    .append(",\"createdAt\":").append(createdAt);

            StringBuilder lh = new StringBuilder(160).append("{\"orderId\":").append(orderId)
                    .append(",\"userId\":").append(userId).append(",\"status\":");
            appendString(lh, status);
            lh.append(",\"orderType\":");
            appendString(lh, orderType);
            lh.append(",\"createdAt\":").append(createdAt);
            lineHead = lh.toString();

            items = new StringBuilder(256);
            firstItem = true;
        }

        private void closeOrder() {
            if (currentOrderId < 0) {
                return;
            }
            String doc = orderHead.append(",\"items\":[").append(items).append("]}").toString();
            batch.add(new String[] {ORDER_PREFIX + currentOrderId, "$", doc});
            orderIds.add(currentOrderId);
            orders++;
            ordersInBatch++;
            currentOrderId = -1;
            if (ordersInBatch >= ORDERS_PER_PIPELINE) {
                flush();
            }
        }

        void finish() {
            closeOrder();
            flush();
            writer.awaitAll();
        }

        /** Giao lô cho luồng ghi; chặn khi đã có đủ lô đang chờ, nên bộ nhớ bị chặn trên. */
        private void flush() {
            if (batch.isEmpty()) {
                return;
            }
            List<String[]> toWrite = new ArrayList<>(batch);
            batch.clear();
            ordersInBatch = 0;
            writer.submit(toWrite);
        }
    }

    /**
     * Ghi các lô JSON.SET song song với việc đọc Oracle: WRITER_THREADS luồng, mỗi lô một pipeline trên một kết nối,
     * tối đa WRITER_THREADS + 1 lô nằm trong bộ nhớ cùng lúc.
     */
    private final class Writer implements AutoCloseable {
        private final ExecutorService pool = Executors.newFixedThreadPool(WRITER_THREADS, r -> {
            Thread t = new Thread(r, "analytics-rebuild-writer");
            t.setDaemon(true);
            return t;
        });
        private final Semaphore slots = new Semaphore(WRITER_THREADS + 1);
        private final List<Future<?>> pending = new ArrayList<>();
        private final AtomicLong writeNanos = new AtomicLong();

        void submit(List<String[]> commands) {
            checkFailures();
            slots.acquireUninterruptibly();
            pending.add(pool.submit(() -> {
                long t0 = System.nanoTime();
                try {
                    writeBatch(commands);
                } finally {
                    writeNanos.addAndGet(System.nanoTime() - t0);
                    slots.release();
                }
            }));
        }

        private void checkFailures() {
            pending.removeIf(f -> {
                if (!f.isDone()) {
                    return false;
                }
                await(f);
                return true;
            });
        }

        void awaitAll() {
            for (Future<?> f : pending) {
                await(f);
            }
            pending.clear();
        }

        private void await(Future<?> f) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Rebuild bị ngắt", null, e);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof RuntimeException re) {
                    throw RedisCommandRunner.translate(re);
                }
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Lỗi khi ghi Redis", null, e.getCause());
            }
        }

        @Override
        public void close() {
            pool.shutdownNow();
        }
    }

    private void writeBatch(List<String[]> commands) {
        redis.withPipeline((AbstractPipeline p) -> {
            List<Response<Object>> responses = new ArrayList<>(commands.size());
            for (String[] cmd : commands) {
                responses.add(p.sendCommand(JSON_SET, cmd));
            }
            p.sync();
            for (Response<Object> r : responses) {
                r.get(); // ném JedisDataException nếu một JSON.SET lỗi
            }
            return null;
        });
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static void appendString(StringBuilder sb, String s) {
        if (s == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

}
