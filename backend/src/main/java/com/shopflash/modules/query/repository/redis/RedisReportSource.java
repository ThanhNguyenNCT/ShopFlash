package com.shopflash.modules.query.repository.redis;

import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.LINE_INDEX;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.ORDER_INDEX;
import static com.shopflash.modules.query.repository.redis.RedisReplies.aggregateRows;
import static com.shopflash.modules.query.repository.redis.RedisReplies.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Repository;

import com.shopflash.modules.query.dto.response.CategoryRevenueRow;
import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.PageInfo;
import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.TopProductRow;
import com.shopflash.modules.query.dto.response.VipCustomerRow;
import com.shopflash.modules.query.dto.response.VipSummary;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.ReportSource;

/**
 * Báo cáo trên read model Redis. AGGREGATE: FT.AGGREGATE tính trên Redis. CLIENT: kéo document về bằng cursor
 * (FT.AGGREGATE ... WITHCURSOR + FT.CURSOR READ, vì FT.SEARCH bị chặn ở MAXSEARCHRESULTS=10000) rồi tính trong Java.
 * Hai chế độ phải ra cùng một kết quả.
 */
@Repository
public class RedisReportSource implements ReportSource {

    static final int CURSOR_COUNT = 10000;
    static final String UNSOLD_NOTE = "Redis không có NOT EXISTS/anti-join: read model chỉ chứa sản phẩm đã bán, "
            + "muốn biết sản phẩm chưa bán thì ứng dụng phải tự duy trì tập sản phẩm đã bán (SET) và lấy hiệu với "
            + "danh sách toàn bộ sản phẩm.";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final RedisCommandRunner redis;

    public RedisReportSource(RedisCommandRunner redis) {
        this.redis = redis;
    }

    @Override
    public Source source() {
        return Source.REDIS;
    }

    private static RedisMode mode(ReportQuery q) {
        return q.redisMode() == null ? RedisMode.AGGREGATE : q.redisMode();
    }

    @Override
    public SourceResult run(ReportQuery q) {
        RedisMode mode = mode(q);
        if (q.report() == Report.UNSOLD_PRODUCTS) {
            return SourceResult.unsupported(mode.name(), UNSOLD_NOTE);
        }
        Trace trace = new Trace();
        ReportData data = mode == RedisMode.AGGREGATE ? aggregate(q, trace) : client(q, trace);
        return SourceResult.of(data, mode.name(), trace.roundTrips, String.join("\n", trace.statements));
    }

    /** Ghi lại lệnh đã gửi và số round-trip thật. */
    static final class Trace {
        final List<String> statements = new ArrayList<>();
        int roundTrips;
    }

    // ---------------------------------------------------------------- AGGREGATE

    private Object call(Trace t, String[] cmd) {
        t.statements.add(RedisCommandRunner.toCli(cmd));
        t.roundTrips++;
        return redis.call(cmd);
    }

    private ReportData aggregate(ReportQuery q, Trace t) {
        return switch (q.report()) {
            case REVENUE_BY_MONTH -> {
                List<MonthRevenueRow> rows = new ArrayList<>();
                for (Map<String, String> r : aggregateRows(call(t, RedisAggregateCommands.revenueByMonth(q)))) {
                    rows.add(new MonthRevenueRow(r.get("month"), longOf(r.get("orderCount")), money(r.get("revenue"))));
                }
                yield ReportData.of(rows);
            }
            case REVENUE_BY_CATEGORY -> {
                List<CategoryRevenueRow> rows = new ArrayList<>();
                for (Map<String, String> r : aggregateRows(call(t, RedisAggregateCommands.revenueByCategory(q)))) {
                    rows.add(new CategoryRevenueRow(longOf(r.get("categoryId")), r.get("categoryName"),
                            longOf(r.get("quantity")), money(r.get("revenue"))));
                }
                yield ReportData.of(rows);
            }
            case TOP_PRODUCTS -> {
                List<TopProductRow> rows = new ArrayList<>();
                int rank = 1;
                for (Map<String, String> r : aggregateRows(call(t, RedisAggregateCommands.topProducts(q)))) {
                    rows.add(new TopProductRow(rank++, longOf(r.get("productId")), r.get("productName"),
                            r.get("categoryName"), longOf(r.get("quantity")), money(r.get("revenue"))));
                }
                yield ReportData.of(rows);
            }
            case VIP_CUSTOMERS -> vipAggregate(q, t);
            case UNSOLD_PRODUCTS -> throw new IllegalStateException("unsupported");
        };
    }

    private ReportData vipAggregate(ReportQuery q, Trace t) {
        List<Map<String, String>> avgRows = aggregateRows(call(t, RedisAggregateCommands.vipAverage(q)));
        if (avgRows.isEmpty() || avgRows.get(0).get("averageSpent") == null) {
            return new ReportData(List.of(), new PageInfo(q.page(), q.size(), 0),
                    new VipSummary(BigDecimal.ZERO.setScale(2), 0));
        }
        String average = avgRows.get(0).get("averageSpent");
        long customerCount = longOf(avgRows.get(0).get("customerCount"));

        // Lượt 2: trang VIP và tổng số VIP gửi chung một pipeline (một round-trip).
        String[] page = RedisAggregateCommands.vipPage(q, average);
        String[] count = RedisAggregateCommands.vipCount(q, average);
        t.statements.add(RedisCommandRunner.toCli(page));
        t.statements.add(RedisCommandRunner.toCli(count));
        t.roundTrips++;
        List<Object> replies = redis.pipeline(List.of(page, count));

        List<VipCustomerRow> rows = new ArrayList<>();
        for (Map<String, String> r : aggregateRows(replies.get(0))) {
            rows.add(new VipCustomerRow(longOf(r.get("userId")), r.get("userName"), r.get("userEmail"),
                    longOf(r.get("orderCount")), money(r.get("totalSpent"))));
        }
        List<Map<String, String>> countRows = aggregateRows(replies.get(1));
        long total = countRows.isEmpty() ? 0 : longOf(countRows.get(0).get("vipCount"));
        return new ReportData(rows, new PageInfo(q.page(), q.size(), total),
                new VipSummary(money(average), customerCount));
    }

    // ---------------------------------------------------------------- CLIENT

    /**
     * Lệnh lấy document cho CLIENT: chỉ lọc theo index, LOAD các trường cần theo alias (đều SORTABLE nên đọc từ
     * index, không parse JSON), đọc theo cursor.
     */
    static String[] clientFetch(ReportQuery q) {
        String filter = RedisAggregateCommands.filter(q);
        String[] load = switch (q.report()) {
            case REVENUE_BY_MONTH -> new String[] {"2", "@createdAt", "@totalAmount"};
            case REVENUE_BY_CATEGORY -> new String[] {"4", "@categoryId", "@quantity", "@lineTotal", "@categoryName"};
            case TOP_PRODUCTS -> new String[] {"5", "@productId", "@quantity", "@lineTotal", "@productName",
                    "@categoryName"};
            case VIP_CUSTOMERS -> new String[] {"4", "@userId", "@totalAmount", "@userName", "@userEmail"};
            case UNSOLD_PRODUCTS -> throw new IllegalStateException("unsupported");
        };
        String index = clientIndex(q.report());
        List<String> cmd = new ArrayList<>(List.of("FT.AGGREGATE", index, filter, "TIMEOUT", "0", "LOAD"));
        cmd.addAll(List.of(load));
        cmd.addAll(List.of("WITHCURSOR", "COUNT", String.valueOf(CURSOR_COUNT)));
        return cmd.toArray(String[]::new);
    }

    static String clientIndex(Report report) {
        return report == Report.REVENUE_BY_MONTH || report == Report.VIP_CUSTOMERS ? ORDER_INDEX : LINE_INDEX;
    }

    /** Đọc hết cursor, mỗi document gọi sink một lần; trả số round-trip. */
    private void fetchAll(ReportQuery q, Trace t, Consumer<Map<String, String>> sink) {
        String[] first = clientFetch(q);
        String index = first[1];
        Object reply = redis.call(first);
        int pages = 1;
        while (true) {
            List<Object> parts = RedisReplies.list(reply);
            for (Map<String, String> row : aggregateRows(parts.get(0))) {
                sink.accept(row);
            }
            long cursorId = RedisReplies.asLong(parts.get(1));
            if (cursorId == 0) {
                break;
            }
            reply = redis.call("FT.CURSOR", "READ", index, Long.toString(cursorId), "COUNT",
                    String.valueOf(CURSOR_COUNT));
            pages++;
        }
        t.roundTrips += pages;
        t.statements.add(RedisCommandRunner.toCli(first));
        if (pages > 1) {
            t.statements.add("FT.CURSOR READ " + index + " <cursorId> COUNT " + CURSOR_COUNT + " (x" + (pages - 1)
                    + " trang)");
        }
        t.statements.add("# " + pages + " trang; gom nhóm, cộng tổng, sắp xếp làm trong Java, không phải trên Redis");
    }

    private ReportData client(ReportQuery q, Trace t) {
        return switch (q.report()) {
            case REVENUE_BY_MONTH -> {
                Map<String, long[]> counts = new TreeMap<>();
                Map<String, BigDecimal> sums = new HashMap<>();
                fetchAll(q, t, r -> {
                    String month = Instant.ofEpochSecond(longOf(r.get("createdAt")))
                            .atOffset(ReportQuery.ZONE).format(MONTH);
                    counts.computeIfAbsent(month, k -> new long[1])[0]++;
                    sums.merge(month, decimal(r.get("totalAmount")), BigDecimal::add);
                });
                List<MonthRevenueRow> rows = new ArrayList<>();
                counts.forEach((m, c) -> rows.add(new MonthRevenueRow(m, c[0], scale(sums.get(m)))));
                yield ReportData.of(rows);
            }
            case REVENUE_BY_CATEGORY -> {
                Map<Long, Group> groups = new HashMap<>();
                fetchAll(q, t, r -> groups.computeIfAbsent(longOf(r.get("categoryId")),
                        k -> new Group(null, r.get("categoryName"))).add(r));
                List<CategoryRevenueRow> rows = new ArrayList<>();
                groups.forEach((id, g) -> rows.add(new CategoryRevenueRow(id, g.categoryName, g.quantity,
                        scale(g.revenue))));
                rows.sort(Comparator.comparing(CategoryRevenueRow::revenue).reversed()
                        .thenComparingLong(CategoryRevenueRow::categoryId));
                yield ReportData.of(rows);
            }
            case TOP_PRODUCTS -> {
                Map<Long, Group> groups = new HashMap<>();
                fetchAll(q, t, r -> groups.computeIfAbsent(longOf(r.get("productId")),
                        k -> new Group(r.get("productName"), r.get("categoryName"))).add(r));
                List<Map.Entry<Long, Group>> sorted = new ArrayList<>(groups.entrySet());
                sorted.sort(Comparator.<Map.Entry<Long, Group>>comparingLong(e -> -e.getValue().quantity)
                        .thenComparingLong(Map.Entry::getKey));
                List<TopProductRow> rows = new ArrayList<>();
                for (int i = 0; i < Math.min(q.limit(), sorted.size()); i++) {
                    Map.Entry<Long, Group> e = sorted.get(i);
                    Group g = e.getValue();
                    rows.add(new TopProductRow(i + 1, e.getKey(), g.productName, g.categoryName, g.quantity,
                            scale(g.revenue)));
                }
                yield ReportData.of(rows);
            }
            case VIP_CUSTOMERS -> vipClient(q, t);
            case UNSOLD_PRODUCTS -> throw new IllegalStateException("unsupported");
        };
    }

    private ReportData vipClient(ReportQuery q, Trace t) {
        Map<Long, Customer> customers = new HashMap<>();
        fetchAll(q, t, r -> {
            Customer c = customers.computeIfAbsent(longOf(r.get("userId")),
                    id -> new Customer(id, r.get("userName"), r.get("userEmail")));
            c.orders++;
            c.spent = c.spent.add(decimal(r.get("totalAmount")));
        });
        long n = customers.size();
        if (n == 0) {
            return new ReportData(List.of(), new PageInfo(q.page(), q.size(), 0),
                    new VipSummary(BigDecimal.ZERO.setScale(2), 0));
        }
        BigDecimal sum = customers.values().stream().map(c -> c.spent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal count = BigDecimal.valueOf(n);
        // totalSpent > sum / n  <=>  totalSpent * n > sum (so sánh chính xác, không chia).
        List<Customer> vips = new ArrayList<>(customers.values().stream()
                .filter(c -> c.spent.multiply(count).compareTo(sum) > 0).toList());
        vips.sort(Comparator.comparing((Customer c) -> c.spent).reversed().thenComparingLong(c -> c.userId));
        List<VipCustomerRow> rows = vips.stream().skip(q.offset()).limit(q.size())
                .map(c -> new VipCustomerRow(c.userId, c.name, c.email, c.orders, scale(c.spent)))
                .toList();
        return new ReportData(rows, new PageInfo(q.page(), q.size(), vips.size()),
                new VipSummary(sum.divide(count, 2, RoundingMode.HALF_UP), n));
    }

    private static final class Group {
        final String productName;
        final String categoryName;
        long quantity;
        BigDecimal revenue = BigDecimal.ZERO;

        Group(String productName, String categoryName) {
            this.productName = productName;
            this.categoryName = categoryName;
        }

        void add(Map<String, String> r) {
            quantity += longOf(r.get("quantity"));
            revenue = revenue.add(decimal(r.get("lineTotal")));
        }
    }

    private static final class Customer {
        final long userId;
        final String name;
        final String email;
        long orders;
        BigDecimal spent = BigDecimal.ZERO;

        Customer(long userId, String name, String email) {
            this.userId = userId;
            this.name = name;
            this.email = email;
        }
    }

    // ---------------------------------------------------------------- PLAN

    @Override
    public PlanResponse plan(ReportQuery q) {
        RedisMode mode = mode(q);
        if (q.report() == Report.UNSOLD_PRODUCTS) {
            return new PlanResponse("redis", mode.name(), null, List.of(UNSOLD_NOTE));
        }
        return mode == RedisMode.AGGREGATE ? planAggregate(q) : planClient(q);
    }

    private PlanResponse planAggregate(ReportQuery q) {
        List<String> lines = new ArrayList<>();
        List<String> statements = new ArrayList<>();
        String[] target = switch (q.report()) {
            case REVENUE_BY_MONTH -> RedisAggregateCommands.revenueByMonth(q);
            case REVENUE_BY_CATEGORY -> RedisAggregateCommands.revenueByCategory(q);
            case TOP_PRODUCTS -> RedisAggregateCommands.topProducts(q);
            case VIP_CUSTOMERS -> {
                // Lượt 1 chạy thật để có hằng số trung bình, lượt 2 mới được profile.
                String[] first = RedisAggregateCommands.vipAverage(q);
                statements.add(RedisCommandRunner.toCli(first));
                List<Map<String, String>> avg = aggregateRows(redis.call(first));
                String average = avg.isEmpty() || avg.get(0).get("averageSpent") == null
                        ? "0" : avg.get(0).get("averageSpent");
                lines.add("Lượt 1 (đã chạy, không profile): trung bình tổng chi = " + average);
                lines.add("Lượt 2 (profile): lọc khách có tổng chi > " + average);
                yield RedisAggregateCommands.vipPage(q, average);
            }
            case UNSOLD_PRODUCTS -> throw new IllegalStateException("unsupported");
        };
        String[] profile = RedisAggregateCommands.profile(target);
        statements.add(RedisCommandRunner.toCli(profile));
        List<Object> reply = RedisReplies.list(redis.call(profile));
        lines.add("Số dòng kết quả: " + Math.max(0, RedisReplies.list(reply.get(0)).size() - 1));
        lines.addAll(RedisReplies.flatten(reply.size() > 1 ? reply.get(1) : List.of()));
        return new PlanResponse("redis", RedisMode.AGGREGATE.name(), String.join("\n", statements), lines);
    }

    private PlanResponse planClient(ReportQuery q) {
        String index = clientIndex(q.report());
        String[] count = {"FT.SEARCH", index, RedisAggregateCommands.filter(q), "LIMIT", "0", "0"};
        long docs = RedisReplies.asLong(RedisReplies.list(redis.call(count)).get(0));
        long pages = Math.max(1, (docs + CURSOR_COUNT - 1) / CURSOR_COUNT);
        String[] fetch = clientFetch(q);
        String fetchCli = RedisCommandRunner.toCli(fetch);
        List<String> lines = new ArrayList<>();
        lines.add("Chế độ CLIENT: Redis chỉ lọc theo index (status TAG, createdAt NUMERIC) và trả document thô.");
        lines.add("Số document khớp bộ lọc: " + docs + " (đếm bằng " + RedisCommandRunner.toCli(count) + ")");
        lines.add("1. " + fetchCli);
        lines.add("2. FT.CURSOR READ " + index + " <cursorId> COUNT " + CURSOR_COUNT
                + ", lặp đến khi cursorId = 0: khoảng " + (pages - 1) + " lần");
        lines.add("Round-trip dự kiến: khoảng " + pages + " (mỗi trang " + CURSOR_COUNT + " document)");
        lines.add(switch (q.report()) {
            case REVENUE_BY_MONTH -> "Trong Java: đổi createdAt sang tháng +07:00, đếm đơn, cộng totalAmount, sắp theo tháng.";
            case REVENUE_BY_CATEGORY -> "Trong Java: gom theo categoryId, cộng quantity và lineTotal, sắp revenue giảm dần rồi categoryId.";
            case TOP_PRODUCTS -> "Trong Java: gom theo productId, cộng quantity và lineTotal, sắp quantity giảm dần rồi productId, lấy " + q.limit() + " dòng đầu.";
            case VIP_CUSTOMERS -> "Trong Java: gom theo userId, tính trung bình tổng chi, lọc > trung bình, sắp totalSpent giảm dần rồi userId, cắt trang.";
            case UNSOLD_PRODUCTS -> "";
        });
        lines.add("Toàn bộ document đi qua mạng về ứng dụng, Redis không gom nhóm hay sắp xếp gì.");
        return new PlanResponse("redis", RedisMode.CLIENT.name(), fetchCli, lines);
    }

    // ---------------------------------------------------------------- helpers

    static long longOf(String s) {
        return s == null || s.isEmpty() ? 0 : new BigDecimal(s).longValue();
    }

    static BigDecimal decimal(String s) {
        return s == null || s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
    }

    static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
