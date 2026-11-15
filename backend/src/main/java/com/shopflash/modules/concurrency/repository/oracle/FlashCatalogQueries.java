package com.shopflash.modules.concurrency.repository.oracle;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc chương trình và sản phẩm flash sale cho phía khách. Chỉ đọc, không khóa. */
@Repository
public class FlashCatalogQueries {

    public record SaleRow(long saleId, String name, String description, String status, OffsetDateTime startAt,
                          OffsetDateTime endAt, int maxPerUser, int reservationTtlSeconds) {
    }

    public record ItemRow(long itemId, long saleId, long productId, String productName, String thumbnailUrl,
                          BigDecimal basePrice, BigDecimal salePrice, long allocated, long sold, String status) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public FlashCatalogQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Chương trình SCHEDULED/ACTIVE chưa kết thúc và bắt đầu trong horizonDays ngày tới (dùng index status, start_at). */
    public List<SaleRow> visibleSales(int horizonDays) {
        return jdbc.query("""
                SELECT fs.flash_sale_id, fs.name, fs.description, fs.status, fs.start_at, fs.end_at,
                       c.max_quantity_per_user, c.reservation_timeout_seconds
                FROM   flash_sales fs
                JOIN   flash_sale_configs c ON c.flash_sale_id = fs.flash_sale_id
                WHERE  fs.status IN ('SCHEDULED', 'ACTIVE')
                AND    fs.start_at <= SYSTIMESTAMP + NUMTODSINTERVAL(:days, 'DAY')
                AND    fs.end_at > SYSTIMESTAMP
                ORDER  BY fs.start_at, fs.flash_sale_id
                """, Map.of("days", horizonDays), (rs, i) -> new SaleRow(
                rs.getLong("flash_sale_id"), rs.getString("name"), rs.getString("description"),
                rs.getString("status"), rs.getObject("start_at", OffsetDateTime.class),
                rs.getObject("end_at", OffsetDateTime.class), rs.getInt("max_quantity_per_user"),
                rs.getInt("reservation_timeout_seconds")));
    }

    /** Sản phẩm đang bán (status ACTIVE) của các chương trình, theo chương trình rồi theo item. */
    public List<ItemRow> activeItems(Collection<Long> saleIds) {
        if (saleIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT fi.flash_sale_item_id, fi.flash_sale_id, fi.product_id, p.name, p.thumbnail_url,
                       p.base_price, fi.sale_price, fi.allocated_quantity, fi.sold_quantity, fi.status
                FROM   flash_sale_items fi
                JOIN   products p ON p.product_id = fi.product_id
                WHERE  fi.flash_sale_id IN (:saleIds) AND fi.status = 'ACTIVE'
                ORDER  BY fi.flash_sale_id, fi.flash_sale_item_id
                """, Map.of("saleIds", saleIds), FlashCatalogQueries::item);
    }

    public Optional<ItemRow> item(long itemId) {
        return jdbc.query("""
                SELECT fi.flash_sale_item_id, fi.flash_sale_id, fi.product_id, p.name, p.thumbnail_url,
                       p.base_price, fi.sale_price, fi.allocated_quantity, fi.sold_quantity, fi.status
                FROM   flash_sale_items fi
                JOIN   products p ON p.product_id = fi.product_id
                WHERE  fi.flash_sale_item_id = :itemId
                """, Map.of("itemId", itemId), FlashCatalogQueries::item).stream().findFirst();
    }

    private static ItemRow item(ResultSet rs, int rowNum) throws SQLException {
        return new ItemRow(rs.getLong("flash_sale_item_id"), rs.getLong("flash_sale_id"), rs.getLong("product_id"),
                rs.getString("name"), rs.getString("thumbnail_url"), rs.getBigDecimal("base_price"),
                rs.getBigDecimal("sale_price"), rs.getLong("allocated_quantity"), rs.getLong("sold_quantity"),
                rs.getString("status"));
    }
}
