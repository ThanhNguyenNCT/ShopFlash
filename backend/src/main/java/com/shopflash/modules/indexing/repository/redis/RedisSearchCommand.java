package com.shopflash.modules.indexing.repository.redis;

import com.shopflash.modules.indexing.dto.response.ProductItem;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SortOrder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/**
 * Dựng lệnh FT.SEARCH trên idx:products và đọc reply.
 * Từ khóa chỉ gồm chữ và số (Keywords) nên không cần escape; số được in dạng thường (không dạng mũ).
 * Luôn kèm TIMEOUT 0: Redis Stack mặc định TIMEOUT 500 + ON_TIMEOUT return (trả kết quả dở mà không báo lỗi).
 */
public final class RedisSearchCommand {

    public static final String INDEX = "idx:products";
    public static final String PREFIX = "product:";

    private RedisSearchCommand() {
    }

    /** Câu truy vấn RediSearch: các điều kiện nối bằng dấu cách (AND). */
    public static String query(SearchQuery q) {
        List<String> parts = new ArrayList<>();
        if (q.hasKeywords()) {
            parts.add("@name:(" + q.keywords().stream().map(k -> k + "*").collect(Collectors.joining(" ")) + ")");
        }
        parts.add("@status:{ACTIVE}");
        if (q.categoryId() != null) {
            parts.add("@categoryId:[" + q.categoryId() + " " + q.categoryId() + "]");
        }
        if (q.minPrice() != null || q.maxPrice() != null) {
            String min = q.minPrice() == null ? "-inf" : plain(q.minPrice());
            String max = q.maxPrice() == null ? "+inf" : plain(q.maxPrice());
            parts.add("@price:[" + min + " " + max + "]");
        }
        if (q.inStock()) {
            parts.add("@inStock:{true}");
        }
        return String.join(" ", parts);
    }

    public static String[] search(SearchQuery q) {
        List<String> args = new ArrayList<>(List.of("FT.SEARCH", INDEX, query(q)));
        args.addAll(tail(q));
        return args.toArray(String[]::new);
    }

    private static List<String> tail(SearchQuery q) {
        List<String> args = new ArrayList<>();
        if (q.sort() == SortOrder.PRICE_ASC) {
            args.addAll(List.of("SORTBY", "price", "ASC"));
        } else if (q.sort() == SortOrder.PRICE_DESC) {
            args.addAll(List.of("SORTBY", "price", "DESC"));
        }
        args.addAll(List.of("LIMIT", String.valueOf(q.offset()), String.valueOf(q.size()), "TIMEOUT", "0"));
        return args;
    }

    static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Reply RESP2 của FT.SEARCH trên JSON: [tổng, key1, ["$", json1], key2, ["$", json2], ...]. */
    public record Page(long total, List<ProductItem> items) {
    }

    public static Page parse(Object reply, ObjectMapper json) {
        List<Object> all = IndexingRedis.list(reply);
        if (all.isEmpty()) {
            return new Page(0, List.of());
        }
        long total = Long.parseLong(IndexingRedis.str(all.get(0)));
        List<ProductItem> items = new ArrayList<>();
        for (int i = 1; i + 1 < all.size(); i += 2) {
            String key = IndexingRedis.str(all.get(i));
            String doc = jsonField(IndexingRedis.list(all.get(i + 1)));
            if (doc != null) {
                items.add(item(key, json.readValue(doc, RedisProductDoc.class)));
            }
        }
        return new Page(total, items);
    }

    private static String jsonField(List<Object> fields) {
        for (int j = 0; j + 1 < fields.size(); j += 2) {
            if ("$".equals(IndexingRedis.str(fields.get(j)))) {
                return IndexingRedis.str(fields.get(j + 1));
            }
        }
        return null;
    }

    static ProductItem item(String key, RedisProductDoc d) {
        long id = d.id() != null ? d.id() : Long.parseLong(key.substring(PREFIX.length()));
        return new ProductItem(id, d.sku(), d.name(), d.categoryId(), d.categoryName(), d.price(),
                Boolean.TRUE.equals(d.inStock()), d.thumbnailUrl());
    }
}
