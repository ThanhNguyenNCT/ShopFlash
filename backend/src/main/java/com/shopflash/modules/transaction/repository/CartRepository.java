package com.shopflash.modules.transaction.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.shopflash.modules.transaction.dto.CartResponse;

@Repository
public class CartRepository {
    private static final String READ_CART = """
            SELECT c.version, ci.product_id, ci.quantity, p.name, p.sku, p.base_price, p.thumbnail_url
              FROM carts c
              LEFT JOIN cart_items ci ON ci.cart_id = c.cart_id
              LEFT JOIN products p ON p.product_id = ci.product_id
             WHERE c.user_id = :userId
             ORDER BY ci.product_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public CartRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CartResponse read(long userId) {
        // Một câu SELECT giữ cùng Oracle statement snapshot cho version và danh sách dòng.
        return jdbc.query(READ_CART, Map.of("userId", userId), rs -> {
            long version = 0;
            boolean cartFound = false;
            BigDecimal total = new BigDecimal("0.00");
            List<CartResponse.Item> items = new ArrayList<>();
            while (rs.next()) {
                if (!cartFound) {
                    version = rs.getLong("version");
                    cartFound = true;
                }
                if (rs.getObject("product_id") == null) continue;
                long productId = rs.getLong("product_id");
                int quantity = rs.getInt("quantity");
                BigDecimal unitPrice = rs.getBigDecimal("base_price");
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
                items.add(new CartResponse.Item(productId, rs.getString("name"), rs.getString("sku"),
                        quantity, unitPrice, lineTotal, rs.getString("thumbnail_url")));
                total = total.add(lineTotal);
            }
            return new CartResponse(version, List.copyOf(items), total);
        });
    }

    public CartState lock(long userId) {
        List<CartState> rows = jdbc.query("""
                SELECT cart_id, version FROM carts WHERE user_id = :userId FOR UPDATE WAIT 3
                """, Map.of("userId", userId),
                (rs, row) -> new CartState(rs.getLong("cart_id"), rs.getLong("version")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void create(long userId) {
        jdbc.update("""
                INSERT INTO carts (user_id, created_at, updated_at)
                VALUES (:userId, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("userId", userId));
    }

    public String productStatus(long productId) {
        List<String> rows = jdbc.query("SELECT status FROM products WHERE product_id = :productId",
                Map.of("productId", productId), (rs, row) -> rs.getString("status"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Integer quantity(long cartId, long productId) {
        List<Integer> rows = jdbc.query("""
                SELECT quantity FROM cart_items WHERE cart_id = :cartId AND product_id = :productId
                """, Map.of("cartId", cartId, "productId", productId), (rs, row) -> rs.getInt("quantity"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void insertItem(long cartId, long productId, int quantity) {
        jdbc.update("""
                INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                VALUES (:cartId, :productId, :quantity, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("cartId", cartId, "productId", productId, "quantity", quantity));
    }

    public void setQuantity(long cartId, long productId, int quantity) {
        jdbc.update("""
                UPDATE cart_items SET quantity = :quantity, updated_at = SYSTIMESTAMP
                WHERE cart_id = :cartId AND product_id = :productId
                """, Map.of("cartId", cartId, "productId", productId, "quantity", quantity));
    }

    public int deleteItem(long cartId, long productId) {
        return jdbc.update("DELETE FROM cart_items WHERE cart_id = :cartId AND product_id = :productId",
                Map.of("cartId", cartId, "productId", productId));
    }

    public int deleteAll(long cartId) {
        return jdbc.update("DELETE FROM cart_items WHERE cart_id = :cartId", Map.of("cartId", cartId));
    }

    public void incrementVersion(long cartId) {
        jdbc.update("""
                UPDATE carts SET version = version + 1, updated_at = SYSTIMESTAMP
                WHERE cart_id = :cartId
                """, Map.of("cartId", cartId));
    }

    public record CartState(long cartId, long version) {}
}
