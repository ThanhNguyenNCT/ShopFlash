package com.shopflash.modules.concurrency.repository.oracle;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Ghi đơn flash sale. Các câu ghi phải chạy trong transaction của FlashCheckoutService, theo thứ tự khóa của module:
 * FLASH_SALE_ITEMS rồi PRODUCT_INVENTORY. Đọc PRODUCTS không khóa. Ghi bằng điều kiện trong WHERE, không đọc
 * lên Java rồi ghi lại.
 */
@Repository
public class FlashOrderRepository {

    /** Giá và thông tin sản phẩm chép vào order_items. */
    public record OrderLine(long productId, String productName, String productSku, BigDecimal unitPrice) {
    }

    public record NewOrder(String orderCode, String idempotencyKey, long userId, BigDecimal total,
                           String recipientName, String recipientPhone, String shippingAddress) {
    }

    public record StoredOrder(long orderId, String orderCode, long userId, String status, BigDecimal totalAmount) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public FlashOrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * sold += q nếu item còn ACTIVE, không vượt allocated và chương trình còn SCHEDULED/ACTIVE trong Oracle. Điều kiện
     * cuối là chốt chặn khi chương trình đã hủy lịch hoặc hủy mà key Redis chưa kịp xóa. false: không bán được.
     */
    public boolean addSold(long itemId, int quantity) {
        return jdbc.update("""
                UPDATE flash_sale_items fi
                SET    fi.sold_quantity = fi.sold_quantity + :q, fi.updated_at = SYSTIMESTAMP
                WHERE  fi.flash_sale_item_id = :itemId AND fi.status = 'ACTIVE'
                AND    fi.sold_quantity + :q <= fi.allocated_quantity
                AND    EXISTS (SELECT 1 FROM flash_sales fs
                               WHERE fs.flash_sale_id = fi.flash_sale_id AND fs.status IN ('SCHEDULED', 'ACTIVE'))
                """, Map.of("q", quantity, "itemId", itemId)) == 1;
    }

    /**
     * Xuất kho phần đã giữ cho flash sale: on_hand -= q, reserved_flash -= q, version + 1 (để form sửa tồn kho của
     * storage thấy đổi). false: reserved_flash không đủ, nghĩa là bất biến tồn kho đã lệch.
     */
    public boolean takeFlashStock(long productId, int quantity) {
        return jdbc.update("""
                UPDATE product_inventory
                SET    on_hand_quantity = on_hand_quantity - :q,
                       reserved_flash_quantity = reserved_flash_quantity - :q,
                       version = version + 1, updated_at = SYSTIMESTAMP
                WHERE  product_id = :productId AND reserved_flash_quantity >= :q
                """, Map.of("q", quantity, "productId", productId)) == 1;
    }

    public Optional<OrderLine> orderLine(long itemId) {
        return jdbc.query("""
                SELECT p.product_id, p.name, p.sku, fi.sale_price
                FROM   flash_sale_items fi
                JOIN   products p ON p.product_id = fi.product_id
                WHERE  fi.flash_sale_item_id = :itemId
                """, Map.of("itemId", itemId), (rs, i) -> new OrderLine(rs.getLong("product_id"),
                        rs.getString("name"), rs.getString("sku"), rs.getBigDecimal("sale_price")))
                .stream().findFirst();
    }

    /** Ghi orders; trả order_id. uk_orders_idempotency chặn đơn thứ hai cho cùng reservation. */
    public long insertOrder(NewOrder order) {
        jdbc.update("""
                INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount,
                                    total_amount, recipient_name, recipient_phone, shipping_address,
                                    idempotency_key, created_at, updated_at)
                VALUES (:code, :userId, 'FLASH_SALE', 'PENDING', :total, 0, :total, :name, :phone, :address,
                        :key, SYSTIMESTAMP, SYSTIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("code", order.orderCode()).addValue("userId", order.userId())
                .addValue("total", order.total()).addValue("name", order.recipientName())
                .addValue("phone", order.recipientPhone()).addValue("address", order.shippingAddress())
                .addValue("key", order.idempotencyKey()));
        return jdbc.queryForObject("SELECT order_id FROM orders WHERE idempotency_key = :key",
                Map.of("key", order.idempotencyKey()), Long.class);
    }

    public void insertOrderItem(long orderId, long itemId, OrderLine line, int quantity) {
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, flash_sale_item_id, product_name, product_sku,
                                         unit_price, quantity, line_total)
                VALUES (:orderId, :productId, :itemId, :name, :sku, :price, :q, :total)
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId).addValue("productId", line.productId()).addValue("itemId", itemId)
                .addValue("name", line.productName()).addValue("sku", line.productSku())
                .addValue("price", line.unitPrice()).addValue("q", quantity)
                .addValue("total", line.unitPrice().multiply(BigDecimal.valueOf(quantity))));
    }

    public Optional<StoredOrder> findByIdempotencyKey(String key) {
        return jdbc.query("""
                SELECT order_id, order_code, user_id, status, total_amount
                FROM   orders WHERE idempotency_key = :key
                """, Map.of("key", key), (rs, i) -> new StoredOrder(rs.getLong("order_id"),
                        rs.getString("order_code"), rs.getLong("user_id"), rs.getString("status"),
                        rs.getBigDecimal("total_amount")))
                .stream().findFirst();
    }
}
