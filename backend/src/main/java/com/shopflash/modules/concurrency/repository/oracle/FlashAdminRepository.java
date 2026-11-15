package com.shopflash.modules.concurrency.repository.oracle;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Admin quản lý chương trình (tạo, sửa, xóa, danh sách sản phẩm). Mọi câu sửa chương trình là compare-and-set theo
 * version và trạng thái: hai admin sửa cùng lúc thì chỉ một bên thắng, bên kia nhận 0 dòng.
 */
@Repository
public class FlashAdminRepository {

    public record SaleSummary(long saleId, String name, String status, OffsetDateTime startAt, OffsetDateTime endAt,
                              int itemCount, long allocatedTotal, long soldTotal) {
    }

    public record SaleRow(long saleId, String name, String description, String status, OffsetDateTime startAt,
                          OffsetDateTime endAt, long version, int maxPerUser, int reservationTtlSeconds,
                          boolean allowCancel, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record ItemRow(long itemId, long productId, String sku, String productName, String productStatus,
                          BigDecimal basePrice, BigDecimal salePrice, long allocated, long sold, String status,
                          long onHand, long reservedFlash) {
    }

    public record ProductStock(long productId, String sku, String name, String status, String thumbnailUrl,
                               BigDecimal basePrice, long onHand, long reservedFlash) {

        public long available() {
            return onHand - reservedFlash;
        }
    }

    public record NewItem(long productId, BigDecimal salePrice, int allocated) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public FlashAdminRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- đọc ----

    public long count(String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM flash_sales WHERE (:status IS NULL OR status = :status)",
                statusParam(status), Long.class);
    }

    /** Một trang chương trình, mới nhất trước, kèm tổng suất (aggregate trên flash_sale_items). */
    public List<SaleSummary> page(String status, int offset, int size) {
        return jdbc.query("""
                SELECT fs.flash_sale_id, fs.name, fs.status, fs.start_at, fs.end_at,
                       COUNT(fi.flash_sale_item_id) AS item_count,
                       NVL(SUM(fi.allocated_quantity), 0) AS allocated_total,
                       NVL(SUM(fi.sold_quantity), 0) AS sold_total
                FROM   flash_sales fs
                LEFT JOIN flash_sale_items fi ON fi.flash_sale_id = fs.flash_sale_id
                WHERE  (:status IS NULL OR fs.status = :status)
                GROUP  BY fs.flash_sale_id, fs.name, fs.status, fs.start_at, fs.end_at
                ORDER  BY fs.start_at DESC, fs.flash_sale_id DESC
                OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """, statusParam(status).addValue("offset", offset).addValue("size", size), (rs, i) -> new SaleSummary(
                rs.getLong("flash_sale_id"), rs.getString("name"), rs.getString("status"),
                rs.getObject("start_at", OffsetDateTime.class), rs.getObject("end_at", OffsetDateTime.class),
                rs.getInt("item_count"), rs.getLong("allocated_total"), rs.getLong("sold_total")));
    }

    public Optional<SaleRow> sale(long saleId) {
        return jdbc.query("""
                SELECT fs.flash_sale_id, fs.name, fs.description, fs.status, fs.start_at, fs.end_at, fs.version,
                       c.max_quantity_per_user, c.reservation_timeout_seconds, c.allow_cancel,
                       fs.created_at, fs.updated_at
                FROM   flash_sales fs
                JOIN   flash_sale_configs c ON c.flash_sale_id = fs.flash_sale_id
                WHERE  fs.flash_sale_id = :id
                """, Map.of("id", saleId), (rs, i) -> new SaleRow(rs.getLong("flash_sale_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("status"), rs.getObject("start_at", OffsetDateTime.class),
                rs.getObject("end_at", OffsetDateTime.class), rs.getLong("version"),
                rs.getInt("max_quantity_per_user"), rs.getInt("reservation_timeout_seconds"),
                "Y".equals(rs.getString("allow_cancel")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)))
                .stream().findFirst();
    }

    /** Sản phẩm của chương trình kèm tồn kho (join flash_sale_items, products, product_inventory). */
    public List<ItemRow> items(long saleId) {
        return jdbc.query("""
                SELECT fi.flash_sale_item_id, fi.product_id, p.sku, p.name, p.status AS product_status, p.base_price,
                       fi.sale_price, fi.allocated_quantity, fi.sold_quantity, fi.status,
                       NVL(i.on_hand_quantity, 0) AS on_hand, NVL(i.reserved_flash_quantity, 0) AS reserved_flash
                FROM   flash_sale_items fi
                JOIN   products p ON p.product_id = fi.product_id
                LEFT JOIN product_inventory i ON i.product_id = fi.product_id
                WHERE  fi.flash_sale_id = :id
                ORDER  BY fi.flash_sale_item_id
                """, Map.of("id", saleId), (rs, i) -> new ItemRow(rs.getLong("flash_sale_item_id"),
                rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"), rs.getString("product_status"),
                rs.getBigDecimal("base_price"), rs.getBigDecimal("sale_price"), rs.getLong("allocated_quantity"),
                rs.getLong("sold_quantity"), rs.getString("status"), rs.getLong("on_hand"),
                rs.getLong("reserved_flash")));
    }

    public List<ProductStock> productStocks(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT p.product_id, p.sku, p.name, p.status, p.thumbnail_url, p.base_price,
                       NVL(i.on_hand_quantity, 0) AS on_hand, NVL(i.reserved_flash_quantity, 0) AS reserved_flash
                FROM   products p
                LEFT JOIN product_inventory i ON i.product_id = p.product_id
                WHERE  p.product_id IN (:ids)
                """, Map.of("ids", productIds), FlashAdminRepository::productStock);
    }

    /** Sản phẩm (trong danh sách) đã nằm trong chương trình SCHEDULED/ACTIVE khác có giờ chồng lên [startAt, endAt). */
    public List<Long> productsInOverlappingSales(long saleId, OffsetDateTime startAt, OffsetDateTime endAt,
            Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT DISTINCT fi.product_id
                FROM   flash_sale_items fi
                JOIN   flash_sales fs ON fs.flash_sale_id = fi.flash_sale_id
                WHERE  fi.product_id IN (:ids)
                AND    fs.flash_sale_id <> :saleId
                AND    fs.status IN ('SCHEDULED', 'ACTIVE')
                AND    fs.start_at < :endAt AND fs.end_at > :startAt
                ORDER  BY fi.product_id
                """, new MapSqlParameterSource("ids", productIds).addValue("saleId", saleId)
                .addValue("startAt", startAt).addValue("endAt", endAt), Long.class);
    }

    public long countEligible(long saleId, OffsetDateTime startAt, OffsetDateTime endAt, String like) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM   products p
                JOIN   product_inventory i ON i.product_id = p.product_id
                WHERE  p.status = 'ACTIVE'
                AND    i.on_hand_quantity - i.reserved_flash_quantity > 0
                AND    (:pattern IS NULL OR UPPER(p.name) LIKE :pattern ESCAPE '\\' OR UPPER(p.sku) LIKE :pattern ESCAPE '\\')
                AND    NOT EXISTS (
                         SELECT 1 FROM flash_sale_items fi
                         JOIN   flash_sales fs ON fs.flash_sale_id = fi.flash_sale_id
                         WHERE  fi.product_id = p.product_id
                         AND    fs.flash_sale_id <> :saleId
                         AND    fs.status IN ('SCHEDULED', 'ACTIVE')
                         AND    fs.start_at < :endAt AND fs.end_at > :startAt)
                """, eligibleParams(saleId, startAt, endAt, like), Long.class);
    }

    /**
     * Sản phẩm ACTIVE còn hàng chưa thuộc chương trình SCHEDULED/ACTIVE nào trùng giờ (subquery NOT EXISTS).
     * like là mẫu LIKE đã viết hoa và escape (hoặc null).
     */
    public List<ProductStock> eligible(long saleId, OffsetDateTime startAt, OffsetDateTime endAt, String like,
            int offset, int size) {
        return jdbc.query("""
                SELECT p.product_id, p.sku, p.name, p.status, p.thumbnail_url, p.base_price,
                       i.on_hand_quantity AS on_hand, i.reserved_flash_quantity AS reserved_flash
                FROM   products p
                JOIN   product_inventory i ON i.product_id = p.product_id
                WHERE  p.status = 'ACTIVE'
                AND    i.on_hand_quantity - i.reserved_flash_quantity > 0
                AND    (:pattern IS NULL OR UPPER(p.name) LIKE :pattern ESCAPE '\\' OR UPPER(p.sku) LIKE :pattern ESCAPE '\\')
                AND    NOT EXISTS (
                         SELECT 1 FROM flash_sale_items fi
                         JOIN   flash_sales fs ON fs.flash_sale_id = fi.flash_sale_id
                         WHERE  fi.product_id = p.product_id
                         AND    fs.flash_sale_id <> :saleId
                         AND    fs.status IN ('SCHEDULED', 'ACTIVE')
                         AND    fs.start_at < :endAt AND fs.end_at > :startAt)
                ORDER  BY p.name, p.product_id
                OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """, eligibleParams(saleId, startAt, endAt, like).addValue("offset", offset).addValue("size", size),
                FlashAdminRepository::productStock);
    }

    // ---- ghi ----

    public long insertSale(String name, String description, OffsetDateTime startAt, OffsetDateTime endAt) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO flash_sales (name, description, start_at, end_at, status, version, created_at, updated_at)
                VALUES (:name, :description, :startAt, :endAt, 'DRAFT', 0, SYSTIMESTAMP, SYSTIMESTAMP)
                """, new MapSqlParameterSource("name", name).addValue("description", description, Types.VARCHAR)
                .addValue("startAt", startAt).addValue("endAt", endAt), keys, new String[] {"flash_sale_id"});
        return keys.getKey().longValue();
    }

    public void insertConfig(long saleId, int maxPerUser, int reservationTtlSeconds) {
        jdbc.update("""
                INSERT INTO flash_sale_configs (flash_sale_id, max_quantity_per_user, reservation_timeout_seconds,
                                                allow_cancel, created_at, updated_at)
                VALUES (:id, :max, :ttl, 'N', SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("id", saleId, "max", maxPerUser, "ttl", reservationTtlSeconds));
    }

    /** Sửa toàn bộ chương trình DRAFT nếu version chưa đổi. */
    public boolean updateDraft(long saleId, long version, String name, String description, OffsetDateTime startAt,
            OffsetDateTime endAt) {
        return jdbc.update("""
                UPDATE flash_sales
                SET    name = :name, description = :description, start_at = :startAt, end_at = :endAt,
                       version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND version = :version AND status = 'DRAFT'
                """, new MapSqlParameterSource("id", saleId).addValue("version", version).addValue("name", name)
                .addValue("description", description, Types.VARCHAR).addValue("startAt", startAt)
                .addValue("endAt", endAt)) == 1;
    }

    public void updateConfig(long saleId, int maxPerUser, int reservationTtlSeconds) {
        jdbc.update("""
                UPDATE flash_sale_configs
                SET    max_quantity_per_user = :max, reservation_timeout_seconds = :ttl, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id
                """, Map.of("id", saleId, "max", maxPerUser, "ttl", reservationTtlSeconds));
    }

    /** Chương trình đã lên lịch hoặc đang chạy chỉ sửa được tên và mô tả. */
    public boolean updateInfo(long saleId, long version, String name, String description) {
        return jdbc.update("""
                UPDATE flash_sales
                SET    name = :name, description = :description, version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND version = :version AND status IN ('SCHEDULED', 'ACTIVE')
                """, new MapSqlParameterSource("id", saleId).addValue("version", version).addValue("name", name)
                .addValue("description", description, Types.VARCHAR)) == 1;
    }

    /**
     * Khóa dòng flash_sales của chương trình DRAFT (và tăng version) trước khi thay danh sách sản phẩm. Lên lịch cùng
     * lúc thì một trong hai bên phải chờ khóa; bên chờ thấy trạng thái hoặc version đã đổi và nhận 0 dòng.
     */
    public boolean lockDraft(long saleId, long version) {
        return jdbc.update("""
                UPDATE flash_sales SET version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND version = :version AND status = 'DRAFT'
                """, Map.of("id", saleId, "version", version)) == 1;
    }

    /** Khóa chương trình DRAFT (không xét version) trước khi xóa. */
    public boolean lockDraftForDelete(long saleId) {
        return jdbc.update("""
                UPDATE flash_sales SET version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'DRAFT'
                """, Map.of("id", saleId)) == 1;
    }

    public void deleteItems(long saleId) {
        jdbc.update("DELETE FROM flash_sale_items WHERE flash_sale_id = :id", Map.of("id", saleId));
    }

    public void insertItem(long saleId, NewItem item) {
        jdbc.update("""
                INSERT INTO flash_sale_items (flash_sale_id, product_id, sale_price, allocated_quantity, sold_quantity,
                                              status, created_at, updated_at)
                VALUES (:saleId, :productId, :price, :allocated, 0, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("saleId", saleId, "productId", item.productId(), "price", item.salePrice(),
                "allocated", item.allocated()));
    }

    /** Xóa cứng chương trình DRAFT (đã khóa bằng lockDraftForDelete trong cùng transaction). */
    public void deleteSale(long saleId) {
        deleteItems(saleId);
        jdbc.update("DELETE FROM flash_sale_configs WHERE flash_sale_id = :id", Map.of("id", saleId));
        jdbc.update("DELETE FROM flash_sales WHERE flash_sale_id = :id", Map.of("id", saleId));
    }

    private static MapSqlParameterSource statusParam(String status) {
        return new MapSqlParameterSource().addValue("status", status, Types.VARCHAR);
    }

    private static MapSqlParameterSource eligibleParams(long saleId, OffsetDateTime startAt, OffsetDateTime endAt,
            String like) {
        return new MapSqlParameterSource("saleId", saleId).addValue("startAt", startAt).addValue("endAt", endAt)
                .addValue("pattern", like, Types.VARCHAR);
    }

    private static ProductStock productStock(ResultSet rs, int rowNum) throws SQLException {
        return new ProductStock(rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"),
                rs.getString("status"), rs.getString("thumbnail_url"), rs.getBigDecimal("base_price"),
                rs.getLong("on_hand"), rs.getLong("reserved_flash"));
    }
}
