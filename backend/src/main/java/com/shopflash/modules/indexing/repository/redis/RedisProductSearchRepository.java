package com.shopflash.modules.indexing.repository.redis;

import com.shopflash.modules.indexing.dto.response.ExplainResponse;
import com.shopflash.modules.indexing.dto.response.ProductItem;
import com.shopflash.modules.indexing.model.RedisVariant;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;
import com.shopflash.modules.indexing.model.SortOrder;
import com.shopflash.modules.indexing.repository.RedisProductSearch;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * Tìm sản phẩm trên Redis. SEARCH: một lệnh FT.SEARCH trả cả trang lẫn tổng số. SCAN: không dùng index, duyệt mọi
 * key product:* rồi lọc trong Java, để trang Lab thấy cái giá của việc không có index (E1-E3: chậm hàng trăm lần).
 */
@Repository
public class RedisProductSearchRepository implements RedisProductSearch {

    private static final int SCAN_COUNT = 1000;
    private static final Set<String> INFO_FIELDS = Set.of("num_docs", "indexing", "percent_indexed",
            "inverted_sz_mb", "offset_vectors_sz_mb", "doc_table_size_mb", "sortable_values_size_mb",
            "key_table_size_mb", "tag_overhead_sz_mb", "text_overhead_sz_mb", "total_index_memory_sz_mb");

    private final IndexingRedis redis;
    private final ObjectMapper json;

    public RedisProductSearchRepository(IndexingRedis redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public SearchResult search(SearchQuery query, RedisVariant variant) {
        return variant == RedisVariant.SCAN ? scan(query) : ftSearch(query);
    }

    private SearchResult ftSearch(SearchQuery query) {
        String[] args = RedisSearchCommand.search(query);
        long started = System.nanoTime();
        Object reply = redis.call(args);
        RedisSearchCommand.Page page = RedisSearchCommand.parse(reply, json);
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        return new SearchResult(page.items(), page.total(), RedisVariant.SEARCH.name(), 1,
                IndexingRedis.toCli(args), elapsedMs);
    }

    private SearchResult scan(SearchQuery query) {
        long started = System.nanoTime();
        int roundTrips = 0;
        int scans = 0;
        List<ProductItem> hits = new ArrayList<>();
        String cursor = "0";
        do {
            List<Object> reply = IndexingRedis.list(
                    redis.call("SCAN", cursor, "MATCH", RedisSearchCommand.PREFIX + "*", "COUNT", String.valueOf(SCAN_COUNT)));
            roundTrips++;
            scans++;
            cursor = IndexingRedis.str(reply.get(0));
            List<Object> keys = IndexingRedis.list(reply.get(1));
            if (keys.isEmpty()) {
                continue;
            }
            List<String[]> gets = new ArrayList<>(keys.size());
            List<String> names = new ArrayList<>(keys.size());
            for (Object k : keys) {
                String key = IndexingRedis.str(k);
                names.add(key);
                gets.add(new String[] {"JSON.GET", key});
            }
            List<Object> docs = redis.pipeline(gets);
            roundTrips++;
            for (int i = 0; i < docs.size(); i++) {
                String doc = IndexingRedis.str(docs.get(i));
                if (doc == null) {
                    continue;
                }
                RedisProductDoc d = json.readValue(doc, RedisProductDoc.class);
                if (matches(query, d)) {
                    hits.add(RedisSearchCommand.item(names.get(i), d));
                }
            }
        } while (!"0".equals(cursor));
        hits.sort(order(query.sort()));
        int from = Math.min(query.offset(), hits.size());
        int to = Math.min(from + query.size(), hits.size());
        List<ProductItem> page = new ArrayList<>(hits.subList(from, to));
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        String statement = "SCAN <cursor> MATCH product:* COUNT " + SCAN_COUNT + "   (" + scans + " lần)\n"
                + "JSON.GET <key>   (mỗi lô SCAN gửi một pipeline)\n"
                + "Lọc, sắp xếp, phân trang trong Java (không dùng index)";
        return new SearchResult(page, hits.size(), RedisVariant.SCAN.name(), roundTrips, statement, elapsedMs);
    }

    /** Cùng điều kiện như câu FT.SEARCH: mỗi từ khóa là tiền tố của một từ trong tên. */
    static boolean matches(SearchQuery q, RedisProductDoc d) {
        if (!"ACTIVE".equals(d.status())) {
            return false;
        }
        if (q.categoryId() != null && !q.categoryId().equals(d.categoryId())) {
            return false;
        }
        BigDecimal price = d.price();
        if (q.minPrice() != null && (price == null || price.compareTo(q.minPrice()) < 0)) {
            return false;
        }
        if (q.maxPrice() != null && (price == null || price.compareTo(q.maxPrice()) > 0)) {
            return false;
        }
        if (q.inStock() && !Boolean.TRUE.equals(d.inStock())) {
            return false;
        }
        if (q.hasKeywords()) {
            List<String> words = words(d.name());
            for (String k : q.keywords()) {
                if (words.stream().noneMatch(w -> w.startsWith(k))) {
                    return false;
                }
            }
        }
        return true;
    }

    static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        if (name == null) {
            return out;
        }
        for (String w : name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}\\p{Mn}]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    private static Comparator<ProductItem> order(SortOrder sort) {
        Comparator<ProductItem> byId = Comparator.comparingLong(ProductItem::productId);
        Comparator<ProductItem> byPrice = Comparator.comparing(ProductItem::price,
                Comparator.nullsLast(Comparator.naturalOrder()));
        return switch (sort) {
            case RELEVANCE -> byId;
            case PRICE_ASC -> byPrice.thenComparing(byId);
            case PRICE_DESC -> byPrice.reversed().thenComparing(byId.reversed());
        };
    }

    @Override
    public ExplainResponse explain(SearchQuery query, RedisVariant variant) {
        if (variant == RedisVariant.SCAN) {
            return new ExplainResponse("redis", variant.name(), "SCAN 0 MATCH product:* COUNT " + SCAN_COUNT,
                    List.of("Không dùng index: duyệt mọi key product:*, đọc từng document bằng JSON.GET,",
                            "rồi lọc, sắp xếp và phân trang trong Java."));
        }
        String[] explainArgs = RedisSearchCommand.explain(query);
        String[] profileArgs = RedisSearchCommand.profile(query);
        List<String> lines = new ArrayList<>();
        lines.add("FT.EXPLAIN (cây truy vấn):");
        for (String line : String.valueOf(IndexingRedis.str(redis.call(explainArgs))).split("\n")) {
            if (!line.isBlank()) {
                lines.add("  " + line);
            }
        }
        lines.add("FT.PROFILE (thời gian từng bước, ms):");
        List<Object> profile = IndexingRedis.list(redis.call(profileArgs));
        if (profile.size() > 1) {
            flatten(profile.get(1), 1, lines);
        }
        return new ExplainResponse("redis", variant.name(),
                IndexingRedis.toCli(explainArgs) + "\n" + IndexingRedis.toCli(profileArgs), lines);
    }

    @Override
    public Map<String, String> indexInfo() {
        List<Object> info = IndexingRedis.list(redis.call("FT.INFO", RedisSearchCommand.INDEX));
        Map<String, String> out = new LinkedHashMap<>();
        out.put("index", RedisSearchCommand.INDEX);
        for (int i = 0; i + 1 < info.size(); i += 2) {
            String key = IndexingRedis.str(info.get(i));
            if (INFO_FIELDS.contains(key) && !(info.get(i + 1) instanceof List<?>)) {
                out.put(key, IndexingRedis.str(info.get(i + 1)));
            }
        }
        return out;
    }

    /** Làm phẳng reply lồng nhau của FT.PROFILE: cặp khóa=giá trị gộp một dòng, mảng con thụt vào một cấp. */
    static void flatten(Object node, int depth, List<String> out) {
        String indent = "  ".repeat(depth);
        if (!(node instanceof List<?> items)) {
            out.add(indent + IndexingRedis.str(node));
            return;
        }
        List<String> line = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof List<?>) {
                flush(indent, line, out);
                flatten(item, depth + 1, out);
            } else if (i + 1 < items.size() && !(items.get(i + 1) instanceof List<?>)) {
                line.add(IndexingRedis.str(item) + "=" + IndexingRedis.str(items.get(i + 1)));
                i++;
            } else {
                flush(indent, line, out);
                out.add(indent + IndexingRedis.str(item) + ":");
            }
        }
        flush(indent, line, out);
    }

    private static void flush(String indent, List<String> line, List<String> out) {
        if (!line.isEmpty()) {
            out.add(indent + String.join(", ", line));
            line.clear();
        }
    }
}
