package com.shopflash.modules.transaction.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Hậu kiểm Oracle trên kết nối độc lập sau từng lượt k6, trước khi reset fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
class TransactionHttpAuditIT {
    @Autowired NamedParameterJdbcTemplate jdbc;

    @Test
    void auditCommittedHttpFlowState() throws Exception {
        String rawIds = System.getProperty("tx.fixture.ids", "");
        int expectedOrders = Integer.getInteger("tx.audit.expected.orders", -1);
        int cartSize = Integer.getInteger("tx.audit.cart.size", -1);
        String output = System.getProperty("tx.audit.output", "");
        if (rawIds.isBlank() || expectedOrders < 0 || (cartSize != 1 && cartSize != 5) || output.isBlank()) {
            throw new IllegalArgumentException("Cần fixture IDs, số đơn, kích thước giỏ và đường dẫn audit");
        }
        List<String> ids = List.of(rawIds.split(","));
        long orders = 0;
        long notCancelled = 0;
        long cartItems = 0;
        long lines = 0;
        long stockMismatches = 0;
        for (String id : ids) {
            String email = "txlab-" + id + "@shopflash.local";
            String slug = "txlab-" + id;
            Map<String, ?> emailParam = Map.of("email", email);
            orders += count("SELECT COUNT(*) FROM orders WHERE user_id IN (SELECT user_id FROM users WHERE email = :email)", emailParam);
            notCancelled += count("""
                    SELECT COUNT(*) FROM orders WHERE status <> 'CANCELLED'
                      AND user_id IN (SELECT user_id FROM users WHERE email = :email)
                    """, emailParam);
            cartItems += count("""
                    SELECT COUNT(*) FROM cart_items WHERE cart_id IN
                      (SELECT cart_id FROM carts WHERE user_id IN (SELECT user_id FROM users WHERE email = :email))
                    """, emailParam);
            lines += count("""
                    SELECT COUNT(*) FROM order_items WHERE order_id IN
                      (SELECT order_id FROM orders WHERE user_id IN (SELECT user_id FROM users WHERE email = :email))
                    """, emailParam);
            stockMismatches += count("""
                    SELECT COUNT(*) FROM product_inventory
                    WHERE product_id IN (SELECT product_id FROM products WHERE category_id IN
                      (SELECT category_id FROM categories WHERE slug = :slug))
                      AND (on_hand_quantity <> 100 OR reserved_flash_quantity <> 0)
                    """, Map.of("slug", slug));
        }
        boolean passed = orders == expectedOrders && notCancelled == 0 && cartItems == 0
                && lines == (long) expectedOrders * cartSize && stockMismatches == 0;
        Path file = Path.of(output);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, "{\"expected_orders\":" + expectedOrders + ",\"actual_orders\":" + orders
                + ",\"expected_lines\":" + ((long) expectedOrders * cartSize) + ",\"actual_lines\":" + lines
                + ",\"not_cancelled\":" + notCancelled + ",\"cart_items\":" + cartItems
                + ",\"stock_mismatches\":" + stockMismatches + ",\"assertion\":\""
                + (passed ? "PASS" : "FAIL") + "\"}\n", StandardCharsets.UTF_8);
        assertThat(passed).as("Hậu kiểm Oracle sau k6: %s", file).isTrue();
    }

    private long count(String sql, Map<String, ?> params) {
        return jdbc.queryForObject(sql, params, Long.class);
    }
}
