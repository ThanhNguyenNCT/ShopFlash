package com.shopflash.modules.storage.repository;

import com.shopflash.modules.storage.dto.response.StorageFootprintResponse.OracleSegment;
import com.shopflash.modules.storage.model.CatalogProduct;
import com.shopflash.modules.storage.model.Category;
import com.shopflash.modules.storage.model.CategoryStatus;
import com.shopflash.modules.storage.model.Product;
import com.shopflash.modules.storage.model.ProductInventory;
import com.shopflash.modules.storage.model.ProductStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Tất cả SQL CRUD của Storage; mọi giá trị động đi qua bind variable. */
@Repository
public class StorageRepository {

    private static final String CATEGORY_COLUMNS = """
            category_id, name, slug, description, status, created_at, updated_at
            """;
    private static final String PRODUCT_COLUMNS = """
            p.product_id, p.category_id, p.sku, p.name, p.slug, p.description, p.base_price,
            p.thumbnail_url, p.status AS product_status, p.created_at AS product_created_at,
            p.updated_at AS product_updated_at, c.name AS category_name, c.status AS category_status,
            i.on_hand_quantity, i.reserved_flash_quantity, i.version AS inventory_version,
            i.updated_at AS inventory_updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public StorageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Category> findCategories(int offset, int size, String q, CategoryStatus status) {
        String sql = """
                SELECT %s FROM categories
                WHERE (:q IS NULL OR LOWER(name) LIKE '%%' || LOWER(:q) || '%%')
                  AND (:status IS NULL OR status = :status)
                ORDER BY updated_at DESC, category_id DESC
                OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """.formatted(CATEGORY_COLUMNS);
        return jdbc.query(sql, categoryParams(offset, size, q, status), (rs, row) -> category(rs));
    }

    public long countCategories(String q, CategoryStatus status) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM categories
                WHERE (:q IS NULL OR LOWER(name) LIKE '%%' || LOWER(:q) || '%%')
                  AND (:status IS NULL OR status = :status)
                """, categoryParams(0, 0, q, status), Long.class);
        return count == null ? 0 : count;
    }

    public Category findCategory(long categoryId) {
        return jdbc.query("SELECT " + CATEGORY_COLUMNS + " FROM categories WHERE category_id = :id",
                new MapSqlParameterSource("id", categoryId), (rs, row) -> category(rs)).stream().findFirst().orElse(null);
    }

    public boolean categorySlugExists(String slug, Long exceptId) {
        return exists("SELECT 1 FROM categories WHERE slug = :value AND (:exceptId IS NULL OR category_id <> :exceptId)", slug, exceptId);
    }

    public boolean categoryNameExists(String name, Long exceptId) {
        return exists("SELECT 1 FROM categories WHERE LOWER(name) = LOWER(:value) AND (:exceptId IS NULL OR category_id <> :exceptId)", name, exceptId);
    }

    public long createCategory(String name, String slug, String description, CategoryStatus status) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO categories (name, slug, description, status, created_at, updated_at)
                VALUES (:name, :slug, :description, :status, SYSTIMESTAMP, SYSTIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("name", name).addValue("slug", slug).addValue("description", description).addValue("status", status.name()),
                keys, new String[] {"CATEGORY_ID"});
        return keys.getKey().longValue();
    }

    public void updateCategory(long categoryId, String name, String slug, String description, CategoryStatus status) {
        jdbc.update("""
                UPDATE categories SET name = :name, slug = :slug, description = :description, status = :status,
                    updated_at = SYSTIMESTAMP
                WHERE category_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", categoryId).addValue("name", name).addValue("slug", slug)
                .addValue("description", description).addValue("status", status.name()));
    }

    public long countProductsByCategory(long categoryId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id = :id",
                new MapSqlParameterSource("id", categoryId), Long.class);
        return count == null ? 0 : count;
    }

    public long countActiveProductsByCategory(long categoryId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id = :id AND status = 'ACTIVE'",
                new MapSqlParameterSource("id", categoryId), Long.class);
        return count == null ? 0 : count;
    }

    public void deleteCategory(long categoryId) {
        jdbc.update("DELETE FROM categories WHERE category_id = :id", new MapSqlParameterSource("id", categoryId));
    }

    public List<CatalogProduct> findProducts(int offset, int size, String q, Long categoryId, ProductStatus status) {
        String sql = """
                SELECT %s FROM products p
                JOIN categories c ON c.category_id = p.category_id
                JOIN product_inventory i ON i.product_id = p.product_id
                WHERE (:q IS NULL OR LOWER(p.name) LIKE '%%' || LOWER(:q) || '%%' OR LOWER(p.sku) LIKE '%%' || LOWER(:q) || '%%')
                  AND (:categoryId IS NULL OR p.category_id = :categoryId)
                  AND (:status IS NULL OR p.status = :status)
                ORDER BY p.updated_at DESC, p.product_id DESC
                OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """.formatted(PRODUCT_COLUMNS);
        return jdbc.query(sql, productParams(offset, size, q, categoryId, status), (rs, row) -> catalogProduct(rs));
    }

    public long countProducts(String q, Long categoryId, ProductStatus status) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM products p
                WHERE (:q IS NULL OR LOWER(p.name) LIKE '%%' || LOWER(:q) || '%%' OR LOWER(p.sku) LIKE '%%' || LOWER(:q) || '%%')
                  AND (:categoryId IS NULL OR p.category_id = :categoryId)
                  AND (:status IS NULL OR p.status = :status)
                """, productParams(0, 0, q, categoryId, status), Long.class);
        return count == null ? 0 : count;
    }

    public CatalogProduct findProduct(long productId) {
        String sql = "SELECT " + PRODUCT_COLUMNS + " FROM products p JOIN categories c ON c.category_id = p.category_id "
                + "JOIN product_inventory i ON i.product_id = p.product_id WHERE p.product_id = :id";
        return jdbc.query(sql, new MapSqlParameterSource("id", productId), (rs, row) -> catalogProduct(rs))
                .stream().findFirst().orElse(null);
    }

    public List<CatalogProduct> findProductsForCategory(long categoryId) {
        String sql = "SELECT " + PRODUCT_COLUMNS + " FROM products p JOIN categories c ON c.category_id = p.category_id "
                + "JOIN product_inventory i ON i.product_id = p.product_id WHERE p.category_id = :id ORDER BY p.product_id";
        return jdbc.query(sql, new MapSqlParameterSource("id", categoryId), (rs, row) -> catalogProduct(rs));
    }

    public List<CatalogProduct> findProjectionBatch(long afterProductId, int size) {
        String sql = "SELECT " + PRODUCT_COLUMNS + " FROM products p JOIN categories c ON c.category_id = p.category_id "
                + "JOIN product_inventory i ON i.product_id = p.product_id WHERE p.product_id > :afterId "
                + "ORDER BY p.product_id FETCH FIRST :size ROWS ONLY";
        return jdbc.query(sql, new MapSqlParameterSource().addValue("afterId", afterProductId).addValue("size", size),
                (rs, row) -> catalogProduct(rs));
    }

    public boolean productSkuExists(String sku, Long exceptId) {
        return exists("SELECT 1 FROM products WHERE sku = :value AND (:exceptId IS NULL OR product_id <> :exceptId)", sku, exceptId);
    }

    public boolean productSlugExists(String slug, Long exceptId) {
        return exists("SELECT 1 FROM products WHERE slug = :value AND (:exceptId IS NULL OR product_id <> :exceptId)", slug, exceptId);
    }

    public long createProduct(long categoryId, String sku, String name, String slug, String description, BigDecimal basePrice,
            String thumbnailUrl, ProductStatus status, long onHandQuantity) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO products (category_id, sku, name, slug, description, base_price, thumbnail_url, status, created_at, updated_at)
                VALUES (:categoryId, :sku, :name, :slug, :description, :basePrice, :thumbnailUrl, :status, SYSTIMESTAMP, SYSTIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("categoryId", categoryId).addValue("sku", sku).addValue("name", name).addValue("slug", slug)
                .addValue("description", description).addValue("basePrice", basePrice).addValue("thumbnailUrl", thumbnailUrl)
                .addValue("status", status.name()), keys, new String[] {"PRODUCT_ID"});
        long productId = keys.getKey().longValue();
        jdbc.update("""
                INSERT INTO product_inventory (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
                VALUES (:productId, :onHandQuantity, 0, 0, SYSTIMESTAMP)
                """, new MapSqlParameterSource().addValue("productId", productId).addValue("onHandQuantity", onHandQuantity));
        return productId;
    }

    public void updateProduct(long productId, long categoryId, String sku, String name, String slug, String description,
            BigDecimal basePrice, String thumbnailUrl, ProductStatus status) {
        jdbc.update("""
                UPDATE products SET category_id = :categoryId, sku = :sku, name = :name, slug = :slug,
                    description = :description, base_price = :basePrice, thumbnail_url = :thumbnailUrl,
                    status = :status, updated_at = SYSTIMESTAMP
                WHERE product_id = :id
                """, new MapSqlParameterSource()
                .addValue("id", productId).addValue("categoryId", categoryId).addValue("sku", sku).addValue("name", name)
                .addValue("slug", slug).addValue("description", description).addValue("basePrice", basePrice)
                .addValue("thumbnailUrl", thumbnailUrl).addValue("status", status.name()));
    }

    public int updateInventory(long productId, long onHandQuantity, long version) {
        return jdbc.update("""
                UPDATE product_inventory SET on_hand_quantity = :onHandQuantity, version = version + 1, updated_at = SYSTIMESTAMP
                WHERE product_id = :productId AND version = :version AND :onHandQuantity >= reserved_flash_quantity
                """, new MapSqlParameterSource().addValue("productId", productId)
                .addValue("onHandQuantity", onHandQuantity).addValue("version", version));
    }

    public List<OracleSegment> catalogSegments() {
        return jdbc.query("""
                SELECT segment_name, segment_type, bytes FROM user_segments
                WHERE segment_name IN ('CATEGORIES', 'PRODUCTS', 'PRODUCT_INVENTORY')
                   OR segment_name IN (SELECT index_name FROM user_indexes
                                       WHERE table_name IN ('CATEGORIES', 'PRODUCTS', 'PRODUCT_INVENTORY'))
                ORDER BY segment_type, segment_name
                """, (rs, row) -> new OracleSegment(rs.getString("segment_name"), rs.getString("segment_type"), rs.getLong("bytes")));
    }

    private boolean exists(String sql, String value, Long exceptId) {
        return !jdbc.query(sql, new MapSqlParameterSource().addValue("value", value).addValue("exceptId", exceptId),
                (rs, row) -> 1).isEmpty();
    }

    private static MapSqlParameterSource categoryParams(int offset, int size, String q, CategoryStatus status) {
        return new MapSqlParameterSource().addValue("offset", offset).addValue("size", size)
                .addValue("q", blankToNull(q)).addValue("status", status == null ? null : status.name());
    }

    private static MapSqlParameterSource productParams(int offset, int size, String q, Long categoryId, ProductStatus status) {
        return new MapSqlParameterSource().addValue("offset", offset).addValue("size", size).addValue("q", blankToNull(q))
                .addValue("categoryId", categoryId).addValue("status", status == null ? null : status.name());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Category category(ResultSet rs) throws SQLException {
        return new Category(rs.getLong("category_id"), rs.getString("name"), rs.getString("slug"), rs.getString("description"),
                CategoryStatus.valueOf(rs.getString("status")), time(rs, "created_at"), time(rs, "updated_at"));
    }

    private static CatalogProduct catalogProduct(ResultSet rs) throws SQLException {
        Product product = new Product(rs.getLong("product_id"), rs.getLong("category_id"), rs.getString("sku"), rs.getString("name"),
                rs.getString("slug"), rs.getString("description"), rs.getBigDecimal("base_price"), rs.getString("thumbnail_url"),
                ProductStatus.valueOf(rs.getString("product_status")), time(rs, "product_created_at"), time(rs, "product_updated_at"));
        ProductInventory inventory = new ProductInventory(product.productId(), rs.getLong("on_hand_quantity"),
                rs.getLong("reserved_flash_quantity"), rs.getLong("inventory_version"), time(rs, "inventory_updated_at"));
        return new CatalogProduct(product, rs.getString("category_name"), CategoryStatus.valueOf(rs.getString("category_status")), inventory);
    }

    private static OffsetDateTime time(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, OffsetDateTime.class);
    }
}
