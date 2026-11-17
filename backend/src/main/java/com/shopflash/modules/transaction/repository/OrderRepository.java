package com.shopflash.modules.transaction.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.shopflash.modules.transaction.dto.OrderDetailResponse;
import com.shopflash.modules.transaction.dto.OrderListResponse;

@Repository
public class OrderRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public OrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OrderListResponse list(long userId, int page, int size, String status) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId).addValue("status", status, Types.VARCHAR)
                .addValue("offset", (long) (page - 1) * size).addValue("size", size);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders o
                WHERE o.user_id = :userId AND o.status = NVL(:status, o.status)
                """, params, Long.class);
        List<OrderListResponse.Summary> items = jdbc.query("""
                SELECT o.order_id, o.order_code, o.order_type, o.status, o.created_at, o.total_amount,
                       (SELECT NVL(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.order_id) item_count
                  FROM orders o
                 WHERE o.user_id = :userId AND o.status = NVL(:status, o.status)
                 ORDER BY o.created_at DESC, o.order_id DESC
                 OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
                """, params, (rs, row) -> new OrderListResponse.Summary(
                rs.getLong("order_id"), rs.getString("order_code"), rs.getString("order_type"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getBigDecimal("total_amount"), rs.getLong("item_count")));
        return new OrderListResponse(page, size, total == null ? 0 : total, List.copyOf(items));
    }

    public OrderDetailResponse detail(long userId, long orderId) {
        List<OrderDetailResponse> rows = jdbc.query("""
                SELECT order_id, order_code, order_type, status, recipient_name, recipient_phone,
                       shipping_address, subtotal, discount_amount, total_amount, created_at, cancelled_at
                  FROM orders WHERE order_id = :orderId AND user_id = :userId
                """, new MapSqlParameterSource().addValue("orderId", orderId).addValue("userId", userId),
                (rs, row) -> header(rs));
        if (rows.isEmpty()) return null;
        OrderDetailResponse header = rows.getFirst();
        List<OrderDetailResponse.Line> items = jdbc.query("""
                SELECT oi.order_item_id, oi.product_id, oi.product_name, oi.product_sku,
                       oi.unit_price, oi.quantity, oi.line_total, p.thumbnail_url
                  FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
                  LEFT JOIN products p ON p.product_id = oi.product_id
                 WHERE o.order_id = :orderId AND o.user_id = :userId
                 ORDER BY oi.order_item_id
                """, new MapSqlParameterSource().addValue("orderId", orderId).addValue("userId", userId),
                (rs, row) -> new OrderDetailResponse.Line(rs.getLong("order_item_id"),
                        rs.getLong("product_id"), rs.getString("product_name"), rs.getString("product_sku"),
                        rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total"),
                        rs.getString("thumbnail_url")));
        return new OrderDetailResponse(header.orderId(), header.orderCode(), header.orderType(), header.status(),
                header.recipientName(), header.recipientPhone(), header.shippingAddress(), header.subtotal(),
                header.discountAmount(), header.totalAmount(), header.createdAt(), header.cancelledAt(), List.copyOf(items));
    }

    public LockedOrder lock(long userId, long orderId) {
        List<LockedOrder> rows = jdbc.query("""
                SELECT order_type, status FROM orders
                WHERE order_id = :orderId AND user_id = :userId FOR UPDATE WAIT 3
                """, Map.of("orderId", orderId, "userId", userId),
                (rs, row) -> new LockedOrder(rs.getString("order_type"), rs.getString("status")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<RestoreLine> restoreLines(long orderId) {
        return jdbc.query("""
                SELECT product_id, SUM(quantity) quantity FROM order_items
                WHERE order_id = :orderId GROUP BY product_id ORDER BY product_id
                """, Map.of("orderId", orderId),
                (rs, row) -> new RestoreLine(rs.getLong("product_id"), rs.getLong("quantity")));
    }

    public Long lockInventory(long productId) {
        List<Long> rows = jdbc.query("""
                SELECT on_hand_quantity FROM product_inventory
                WHERE product_id = :productId FOR UPDATE WAIT 3
                """, Map.of("productId", productId), (rs, row) -> rs.getLong("on_hand_quantity"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public int restoreInventory(long productId, long quantity) {
        return jdbc.update("""
                UPDATE product_inventory
                   SET on_hand_quantity = on_hand_quantity + :quantity,
                       version = version + 1, updated_at = SYSTIMESTAMP
                 WHERE product_id = :productId AND on_hand_quantity <= 9999999999 - :quantity
                """, Map.of("productId", productId, "quantity", quantity));
    }

    public int markCancelled(long userId, long orderId) {
        return jdbc.update("""
                UPDATE orders SET status = 'CANCELLED', cancelled_at = SYSTIMESTAMP, updated_at = SYSTIMESTAMP
                WHERE order_id = :orderId AND user_id = :userId
                  AND order_type = 'NORMAL' AND status = 'PENDING'
                """, Map.of("orderId", orderId, "userId", userId));
    }

    public record LockedOrder(String orderType, String status) {}
    public record RestoreLine(long productId, long quantity) {}

    private static OrderDetailResponse header(ResultSet rs) throws SQLException {
        return new OrderDetailResponse(rs.getLong("order_id"), rs.getString("order_code"),
                rs.getString("order_type"), rs.getString("status"), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), rs.getString("shipping_address"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("total_amount"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("cancelled_at", OffsetDateTime.class), List.of());
    }
}
