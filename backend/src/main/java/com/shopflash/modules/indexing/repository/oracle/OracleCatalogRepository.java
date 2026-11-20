package com.shopflash.modules.indexing.repository.oracle;

import com.shopflash.modules.indexing.dto.response.CategoryOption;
import com.shopflash.modules.indexing.dto.response.ProductDetailResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc dữ liệu gốc trên Oracle: chi tiết sản phẩm, danh mục cho bộ lọc. */
@Repository
public class OracleCatalogRepository {

    private static final String DETAIL = """
            SELECT p.product_id, p.sku, p.name, p.description, p.category_id, c.name AS category_name,
                   c.status AS category_status, p.base_price, p.thumbnail_url, p.status,
                   i.on_hand_quantity - i.reserved_flash_quantity AS available_quantity
            FROM products p
            JOIN categories c ON c.category_id = p.category_id
            JOIN product_inventory i ON i.product_id = p.product_id
            WHERE p.product_id = :productId AND p.status = 'ACTIVE'""";

    private static final String CATEGORIES = """
            SELECT c.category_id, c.name, COUNT(p.product_id) AS product_count
            FROM categories c
            LEFT JOIN products p ON p.category_id = c.category_id AND p.status = 'ACTIVE'
            WHERE c.status = 'ACTIVE'
            GROUP BY c.category_id, c.name
            ORDER BY c.name""";

    private final NamedParameterJdbcTemplate jdbc;

    public OracleCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ProductDetailResponse> findActiveProduct(long productId) {
        List<ProductDetailResponse> rows = jdbc.query(DETAIL, new MapSqlParameterSource("productId", productId),
                (rs, i) -> {
                    long available = Math.max(0, rs.getLong("available_quantity"));
                    boolean inStock = "ACTIVE".equals(rs.getString("category_status")) && available > 0;
                    return new ProductDetailResponse(rs.getLong("product_id"), rs.getString("sku"),
                            rs.getString("name"), rs.getString("description"), rs.getLong("category_id"),
                            rs.getString("category_name"), rs.getBigDecimal("base_price"),
                            rs.getString("thumbnail_url"), rs.getString("status"), available, inStock);
                });
        return rows.stream().findFirst();
    }

    public List<CategoryOption> activeCategories() {
        return jdbc.query(CATEGORIES, new MapSqlParameterSource(), (rs, i) -> new CategoryOption(
                rs.getLong("category_id"), rs.getString("name"), rs.getLong("product_count")));
    }
}
