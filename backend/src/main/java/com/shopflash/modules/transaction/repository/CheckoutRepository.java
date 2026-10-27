package com.shopflash.modules.transaction.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.shopflash.modules.transaction.dto.CheckoutPreviewResponse;
import com.shopflash.modules.transaction.dto.PlacedOrderResponse;
import com.shopflash.modules.transaction.service.CheckoutCommand;

@Repository
public class CheckoutRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CheckoutRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Một SELECT giữ giỏ, giá và tồn kho trong cùng Oracle statement snapshot. */
    public CheckoutPreviewResponse preview(long userId) {
        return jdbc.query("""
                SELECT c.version, ci.product_id, ci.quantity, p.name, p.sku, p.base_price,
                       p.status, i.on_hand_quantity, i.reserved_flash_quantity
                  FROM carts c
                  LEFT JOIN cart_items ci ON ci.cart_id = c.cart_id
                  LEFT JOIN products p ON p.product_id = ci.product_id
                  LEFT JOIN product_inventory i ON i.product_id = ci.product_id
                 WHERE c.user_id = :userId
                 ORDER BY ci.product_id
                """, Map.of("userId", userId), rs -> {
            long version = 0;
            BigDecimal total = new BigDecimal("0.00");
            List<CheckoutPreviewResponse.Line> lines = new ArrayList<>();
            LinkedHashSet<String> warnings = new LinkedHashSet<>();
            while (rs.next()) {
                version = rs.getLong("version");
                if (rs.getObject("product_id") == null) continue;
                int quantity = rs.getInt("quantity");
                BigDecimal price = rs.getBigDecimal("base_price");
                BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(quantity));
                long available = rs.getObject("on_hand_quantity") == null ? 0
                        : rs.getLong("on_hand_quantity") - rs.getLong("reserved_flash_quantity");
                String status = rs.getString("status");
                if (!"ACTIVE".equals(status)) warnings.add("PRODUCT_UNAVAILABLE");
                if (available < quantity) warnings.add("OUT_OF_STOCK");
                lines.add(new CheckoutPreviewResponse.Line(rs.getLong("product_id"), rs.getString("name"),
                        rs.getString("sku"), quantity, price, lineTotal, status, available));
                total = total.add(lineTotal);
            }
            if (lines.isEmpty()) warnings.add("CART_EMPTY");
            return new CheckoutPreviewResponse(version, List.copyOf(lines), total, List.copyOf(warnings));
        });
    }

    public List<CartLine> cartLines(long cartId) {
        return jdbc.query("""
                SELECT product_id, quantity FROM cart_items
                WHERE cart_id = :cartId ORDER BY product_id
                """, Map.of("cartId", cartId),
                (rs, row) -> new CartLine(rs.getLong("product_id"), rs.getInt("quantity")));
    }

    public ProductState lockProduct(long productId) {
        List<ProductState> rows = jdbc.query("""
                SELECT product_id, name, sku, base_price, status FROM products
                WHERE product_id = :productId FOR UPDATE WAIT 3
                """, Map.of("productId", productId), (rs, row) -> new ProductState(
                rs.getLong("product_id"), rs.getString("name"), rs.getString("sku"),
                rs.getBigDecimal("base_price"), rs.getString("status")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public InventoryState lockInventory(long productId) {
        List<InventoryState> rows = jdbc.query("""
                SELECT on_hand_quantity, reserved_flash_quantity, version FROM product_inventory
                WHERE product_id = :productId FOR UPDATE WAIT 3
                """, Map.of("productId", productId), (rs, row) -> new InventoryState(
                rs.getLong("on_hand_quantity"), rs.getLong("reserved_flash_quantity"),
                rs.getLong("version")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public StoredOrder storedByKey(String key) {
        List<StoredOrder> rows = jdbc.query("""
                SELECT order_id, order_code, user_id, status, total_amount, checkout_request_hash
                FROM orders WHERE idempotency_key = :key
                """, Map.of("key", key), (rs, row) -> new StoredOrder(
                rs.getLong("order_id"), rs.getString("order_code"), rs.getLong("user_id"),
                rs.getString("status"), rs.getBigDecimal("total_amount"),
                rs.getString("checkout_request_hash")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insertOrder(CheckoutCommand command, String orderCode, BigDecimal total) {
        jdbc.update("""
                INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount,
                                    total_amount, recipient_name, recipient_phone, shipping_address,
                                    idempotency_key, checkout_request_hash, created_at, updated_at)
                VALUES (:code, :userId, 'NORMAL', 'PENDING', :total, 0, :total, :name, :phone,
                        :address, :key, :hash, SYSTIMESTAMP, SYSTIMESTAMP)
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("code", orderCode).addValue("userId", command.userId())
                .addValue("total", total).addValue("name", command.recipientName())
                .addValue("phone", command.recipientPhone()).addValue("address", command.shippingAddress())
                .addValue("key", command.key()).addValue("hash", command.hash()));
    }

    public void insertOrderItem(long orderId, ProductState product, int quantity) {
        BigDecimal total = product.price().multiply(BigDecimal.valueOf(quantity));
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                                         unit_price, quantity, line_total)
                VALUES (:orderId, :productId, :name, :sku, :price, :quantity, :total)
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("orderId", orderId).addValue("productId", product.productId())
                .addValue("name", product.name()).addValue("sku", product.sku())
                .addValue("price", product.price()).addValue("quantity", quantity)
                .addValue("total", total));
    }

    public int decreaseInventory(long productId, int quantity) {
        return jdbc.update("""
                UPDATE product_inventory
                SET on_hand_quantity = on_hand_quantity - :quantity,
                    version = version + 1, updated_at = SYSTIMESTAMP
                WHERE product_id = :productId AND on_hand_quantity - reserved_flash_quantity >= :quantity
                """, Map.of("productId", productId, "quantity", quantity));
    }

    public record CartLine(long productId, int quantity) {}
    public record ProductState(long productId, String name, String sku, BigDecimal price, String status) {}
    public record InventoryState(long onHand, long reservedFlash, long version) {}
    public record StoredOrder(long orderId, String orderCode, long userId, String status,
                              BigDecimal totalAmount, String hash) {
        public PlacedOrderResponse response() {
            return new PlacedOrderResponse(orderId, orderCode, status, totalAmount);
        }
    }
}
