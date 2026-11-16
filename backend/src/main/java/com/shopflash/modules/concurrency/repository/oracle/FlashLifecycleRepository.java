package com.shopflash.modules.concurrency.repository.oracle;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Chuyển trạng thái chương trình và giữ/trả hàng trong kho (AGENTS.md, "Vòng đời chương trình"). Các câu ở đây
 * chạy trong transaction của FlashLifecycleService theo thứ tự khóa của module: FLASH_SALES (compare-and-set
 * trạng thái), FLASH_SALE_ITEMS (FOR UPDATE theo flash_sale_item_id), PRODUCT_INVENTORY (theo product_id tăng dần).
 */
@Repository
public class FlashLifecycleRepository {

    public record SaleState(String status, OffsetDateTime startAt, OffsetDateTime endAt) {
    }

    public record LockedItem(long itemId, long productId, String productStatus, long allocated, long sold) {

        /** Phần còn giữ trong kho cho chương trình: allocated - sold. */
        public long unsold() {
            return allocated - sold;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;

    public FlashLifecycleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SaleState> state(long saleId) {
        return jdbc.query("SELECT status, start_at, end_at FROM flash_sales WHERE flash_sale_id = :id",
                Map.of("id", saleId), (rs, i) -> new SaleState(rs.getString("status"),
                        rs.getObject("start_at", OffsetDateTime.class), rs.getObject("end_at", OffsetDateTime.class)))
                .stream().findFirst();
    }

    // ---- compare-and-set trạng thái (khóa dòng flash_sales) ----

    /** DRAFT → SCHEDULED khi giờ bắt đầu còn ở tương lai. */
    public boolean schedule(long saleId) {
        return jdbc.update("""
                UPDATE flash_sales SET status = 'SCHEDULED', version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'DRAFT' AND start_at > SYSTIMESTAMP
                """, Map.of("id", saleId)) == 1;
    }

    /** SCHEDULED → DRAFT, chỉ khi còn hơn 60 s trước giờ mở bán. */
    public boolean unschedule(long saleId) {
        return jdbc.update("""
                UPDATE flash_sales SET status = 'DRAFT', version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'SCHEDULED'
                AND    start_at > SYSTIMESTAMP + INTERVAL '60' SECOND
                """, Map.of("id", saleId)) == 1;
    }

    public boolean cancelDraft(long saleId) {
        return jdbc.update("""
                UPDATE flash_sales SET status = 'CANCELLED', version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'DRAFT'
                """, Map.of("id", saleId)) == 1;
    }

    /** SCHEDULED → CANCELLED, cùng mốc 60 s như hủy lịch. */
    public boolean cancelScheduled(long saleId) {
        return jdbc.update("""
                UPDATE flash_sales SET status = 'CANCELLED', version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'SCHEDULED'
                AND    start_at > SYSTIMESTAMP + INTERVAL '60' SECOND
                """, Map.of("id", saleId)) == 1;
    }

    /** Dừng sớm: end_at = endAt (giờ Redis) khi chương trình đang ACTIVE và endAt nằm trong khung giờ. */
    public boolean stop(long saleId, OffsetDateTime endAt) {
        return jdbc.update("""
                UPDATE flash_sales SET end_at = :endAt, version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'ACTIVE' AND start_at < :endAt AND end_at > :endAt
                """, new MapSqlParameterSource("id", saleId).addValue("endAt", endAt)) == 1;
    }

    // ---- job ----

    /**
     * Chọn và khóa chương trình SCHEDULED đã tới giờ mở bán. SKIP LOCKED: nhiều instance chạy job cùng lúc thì
     * mỗi instance lấy phần chưa bị khóa, không chờ nhau.
     */
    public List<Long> lockDueForActivation() {
        return jdbc.queryForList("""
                SELECT flash_sale_id FROM flash_sales
                WHERE  status = 'SCHEDULED' AND start_at <= SYSTIMESTAMP
                FOR UPDATE SKIP LOCKED
                """, Map.of(), Long.class);
    }

    public boolean activate(long saleId) {
        return jdbc.update("""
                UPDATE flash_sales SET status = 'ACTIVE', version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'SCHEDULED'
                """, Map.of("id", saleId)) == 1;
    }

    /**
     * Chương trình ACTIVE đã qua end_at + thời gian giữ suất + graceSeconds (để mọi reservation còn hạn kịp chốt trước
     * khi trả phần chưa bán về kho). Chỉ đọc; từng chương trình được khóa riêng bằng lockForEnding.
     */
    public List<Long> dueForEnding(int graceSeconds) {
        return jdbc.queryForList("""
                SELECT fs.flash_sale_id
                FROM   flash_sales fs
                JOIN   flash_sale_configs c ON c.flash_sale_id = fs.flash_sale_id
                WHERE  fs.status = 'ACTIVE'
                AND    fs.end_at + NUMTODSINTERVAL(c.reservation_timeout_seconds + :grace, 'SECOND') <= SYSTIMESTAMP
                ORDER  BY fs.flash_sale_id
                """, Map.of("grace", graceSeconds), Long.class);
    }

    /**
     * Khóa một chương trình tới hạn kết thúc (kiểm tra lại điều kiện). SKIP LOCKED: instance khác đang xử lý chương
     * trình này thì bỏ qua, không chờ. false: không khóa được hoặc không còn tới hạn.
     */
    public boolean lockForEnding(long saleId, int graceSeconds) {
        return !jdbc.queryForList("""
                SELECT fs.flash_sale_id
                FROM   flash_sales fs
                JOIN   flash_sale_configs c ON c.flash_sale_id = fs.flash_sale_id
                WHERE  fs.flash_sale_id = :id AND fs.status = 'ACTIVE'
                AND    fs.end_at + NUMTODSINTERVAL(c.reservation_timeout_seconds + :grace, 'SECOND') <= SYSTIMESTAMP
                FOR UPDATE OF fs.status SKIP LOCKED
                """, Map.of("id", saleId, "grace", graceSeconds), Long.class).isEmpty();
    }

    public boolean end(long saleId) {
        return jdbc.update("""
                UPDATE flash_sales SET status = 'ENDED', version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  flash_sale_id = :id AND status = 'ACTIVE'
                """, Map.of("id", saleId)) == 1;
    }

    // ---- sản phẩm và tồn kho ----

    /** Khóa các item đang bán của chương trình theo flash_sale_item_id tăng dần. */
    public List<LockedItem> lockActiveItems(long saleId) {
        return jdbc.query("""
                SELECT fi.flash_sale_item_id, fi.product_id, fi.allocated_quantity, fi.sold_quantity,
                       p.status AS product_status
                FROM   flash_sale_items fi
                JOIN   products p ON p.product_id = fi.product_id
                WHERE  fi.flash_sale_id = :id AND fi.status = 'ACTIVE'
                ORDER  BY fi.flash_sale_item_id
                FOR UPDATE OF fi.sold_quantity
                """, Map.of("id", saleId), (rs, i) -> new LockedItem(rs.getLong("flash_sale_item_id"),
                rs.getLong("product_id"), rs.getString("product_status"), rs.getLong("allocated_quantity"),
                rs.getLong("sold_quantity")));
    }

    /** Giữ hàng cho flash sale: reserved += q nếu on_hand - reserved >= q. false: không đủ hàng. */
    public boolean reserveStock(long productId, long quantity) {
        return jdbc.update("""
                UPDATE product_inventory
                SET    reserved_flash_quantity = reserved_flash_quantity + :q,
                       version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  product_id = :productId AND on_hand_quantity - reserved_flash_quantity >= :q
                """, Map.of("productId", productId, "q", quantity)) == 1;
    }

    /** Trả phần đã giữ về kho thường: reserved -= q. false: reserved không đủ, nghĩa là bất biến đã lệch. */
    public boolean releaseStock(long productId, long quantity) {
        return jdbc.update("""
                UPDATE product_inventory
                SET    reserved_flash_quantity = reserved_flash_quantity - :q,
                       version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  product_id = :productId AND reserved_flash_quantity >= :q
                """, Map.of("productId", productId, "q", quantity)) == 1;
    }

    /** Dòng lệch của v_inventory_invariant_check cho các sản phẩm của chương trình. */
    public List<Map<String, Object>> invariantViolations(long saleId) {
        return jdbc.queryForList("""
                SELECT v.product_id, v.reserved_flash_quantity, v.expected_reserved
                FROM   v_inventory_invariant_check v
                WHERE  v.product_id IN (SELECT product_id FROM flash_sale_items WHERE flash_sale_id = :id)
                ORDER  BY v.product_id
                """, Map.of("id", saleId));
    }
}
