package com.shopflash.modules.indexing.repository.oracle;

import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SortOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Dựng câu SQL tìm sản phẩm. Câu được ghép từ các đoạn cố định trong lớp này; mọi giá trị từ client đi qua
 * bind variable.
 *
 * Không có từ khóa, lọc danh mục và sắp theo giá: ix_products_cat_status_price (category_id, status, base_price,
 * product_id) cho đúng thứ tự ORDER BY, nên Oracle đọc vừa đủ một trang rồi dừng (E3).
 */
public final class OracleSearchSql {

    public record Sql(String text, MapSqlParameterSource params) {
    }

    static final String COLUMNS = """
            p.product_id, p.sku, p.name, p.category_id, c.name AS category_name, p.base_price, p.thumbnail_url,
                   CASE WHEN c.status = 'ACTIVE' AND i.on_hand_quantity - i.reserved_flash_quantity > 0
                        THEN 1 ELSE 0 END AS in_stock""";

    static final String JOINS = """
            FROM products p
            JOIN categories c ON c.category_id = p.category_id
            JOIN product_inventory i ON i.product_id = p.product_id""";

    private OracleSearchSql() {
    }

    /** Trang kết quả. */
    public static Sql page(SearchQuery q) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        boolean scored = usesScore(q);
        String sql = "SELECT " + COLUMNS + "\n"
                + JOINS + "\n"
                + "WHERE " + where(q, params, scored) + "\n"
                + "ORDER BY " + orderBy(q) + "\n"
                + "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY";
        params.addValue("offset", q.offset()).addValue("size", q.size());
        return new Sql(sql, params);
    }

    /** Tổng số kết quả. Chỉ join categories/product_inventory khi lọc còn hàng (để câu đếm chỉ đọc index khi được). */
    public static Sql count(SearchQuery q) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String from = q.inStock() ? JOINS : "FROM products p";
        String sql = "SELECT COUNT(*)\n"
                + from + "\n"
                + "WHERE " + where(q, params, false);
        return new Sql(sql, params);
    }

    /** Câu truy vấn Oracle Text: mỗi từ tìm theo tiền tố, các từ nối AND. Từ chỉ gồm chữ và số (Keywords). */
    public static String textQuery(List<String> keywords) {
        return keywords.stream().map(k -> k + "%").collect(Collectors.joining(" AND "));
    }

    static boolean usesScore(SearchQuery q) {
        return q.hasKeywords() && q.sort() == SortOrder.RELEVANCE;
    }

    private static String where(SearchQuery q, MapSqlParameterSource params, boolean scored) {
        List<String> parts = new ArrayList<>();
        parts.add("p.status = 'ACTIVE'");
        if (q.hasKeywords()) {
            parts.add(scored ? "CONTAINS(p.name, :textQuery, 1) > 0" : "CONTAINS(p.name, :textQuery) > 0");
            params.addValue("textQuery", textQuery(q.keywords()));
        }
        if (q.categoryId() != null) {
            parts.add("p.category_id = :categoryId");
            params.addValue("categoryId", q.categoryId());
        }
        if (q.minPrice() != null) {
            parts.add("p.base_price >= :minPrice");
            params.addValue("minPrice", q.minPrice());
        }
        if (q.maxPrice() != null) {
            parts.add("p.base_price <= :maxPrice");
            params.addValue("maxPrice", q.maxPrice());
        }
        if (q.inStock()) {
            parts.add("c.status = 'ACTIVE'");
            parts.add("i.on_hand_quantity - i.reserved_flash_quantity > 0");
        }
        return String.join("\n  AND ", parts);
    }

    private static String orderBy(SearchQuery q) {
        return switch (q.sort()) {
            case RELEVANCE -> usesScore(q) ? "SCORE(1) DESC, p.product_id" : "p.product_id";
            case PRICE_ASC -> "p.base_price, p.product_id";
            case PRICE_DESC -> "p.base_price DESC, p.product_id DESC";
        };
    }
}
