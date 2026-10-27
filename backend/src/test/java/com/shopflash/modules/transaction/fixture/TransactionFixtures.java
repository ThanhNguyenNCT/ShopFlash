package com.shopflash.modules.transaction.fixture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/** Fixture của riêng Transaction; mọi câu SQL có tham số đều dùng bind. */
public final class TransactionFixtures {
    private static final Pattern ID = Pattern.compile("[a-z0-9]{1,20}");
    private static final String TEST_BCRYPT_HASH = "$2b$12$uoZ3TNSlBoAKxDE1X39W6uVon9QzLJijSkDUq1AQkFXD6zv8kqz0O";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final RedisClient redis;

    public TransactionFixtures(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager manager, RedisClient redis) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.redis = redis;
    }

    public void setup(String id) {
        Names names = names(id);
        tx.executeWithoutResult(status -> {
            if (!exists(names)) {
                insertOracle(names);
            } else {
                resetOracle(names);
            }
        });
        resetRedis(names);
    }

    public void reset(String id) {
        Names names = names(id);
        tx.executeWithoutResult(status -> {
            if (!exists(names)) throw new IllegalStateException("Fixture chưa được setup: " + id);
            resetOracle(names);
        });
        resetRedis(names);
    }

    public void cleanup(String id) {
        Names names = names(id);
        tx.executeWithoutResult(status -> cleanupOracle(names));
        clearRedis(names);
    }

    public Snapshot snapshot(String id) {
        Names names = names(id);
        Long users = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = :email", Map.of("email", names.email), Long.class);
        Long products = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category_id IN "
                + "(SELECT category_id FROM categories WHERE slug = :slug)", Map.of("slug", names.slug), Long.class);
        Long carts = jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id IN "
                + "(SELECT user_id FROM users WHERE email = :email)", Map.of("email", names.email), Long.class);
        Long items = jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id IN "
                + "(SELECT cart_id FROM carts WHERE user_id IN (SELECT user_id FROM users WHERE email = :email))",
                Map.of("email", names.email), Long.class);
        Long orders = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id IN "
                + "(SELECT user_id FROM users WHERE email = :email)", Map.of("email", names.email), Long.class);
        return new Snapshot(users, products, carts, items, orders, redis.exists(names.redisPrefix + "fixture"));
    }

    public record Snapshot(long users, long products, long carts, long cartItems, long orders, boolean redisReady) {}

    private void insertOracle(Names names) {
        jdbc.update("""
                INSERT INTO users (email, password_hash, full_name, role, status, created_at, updated_at)
                VALUES (:email, :hash, :name, 'CUSTOMER', 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("email", names.email, "hash", TEST_BCRYPT_HASH, "name", "Transaction Fixture " + names.id));
        jdbc.update("""
                INSERT INTO categories (name, slug, status, created_at, updated_at)
                VALUES (:name, :slug, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("name", "Transaction Fixture " + names.id, "slug", names.slug));
        long categoryId = id("SELECT category_id FROM categories WHERE slug = :value", names.slug);
        for (int i = 1; i <= 5; i++) {
            String sku = "TXLAB-" + names.id.toUpperCase() + "-" + i;
            String slug = names.slug + "-product-" + i;
            jdbc.update("""
                    INSERT INTO products (category_id, sku, name, slug, base_price, status, created_at, updated_at)
                    VALUES (:categoryId, :sku, :name, :slug, :price, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                    """, Map.of("categoryId", categoryId, "sku", sku, "name", "Transaction Product " + i,
                    "slug", slug, "price", new BigDecimal("100.00")));
            long productId = id("SELECT product_id FROM products WHERE sku = :value", sku);
            jdbc.update("""
                    INSERT INTO product_inventory (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
                    VALUES (:productId, 100, 0, 0, SYSTIMESTAMP)
                    """, Map.of("productId", productId));
        }
        long userId = id("SELECT user_id FROM users WHERE email = :value", names.email);
        jdbc.update("""
                INSERT INTO carts (user_id, created_at, updated_at)
                VALUES (:userId, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("userId", userId));
    }

    private void resetOracle(Names names) {
        Map<String, ?> email = Map.of("email", names.email);
        jdbc.update("""
                DELETE FROM order_items WHERE order_id IN
                  (SELECT order_id FROM orders WHERE user_id IN (SELECT user_id FROM users WHERE email = :email))
                """, email);
        jdbc.update("DELETE FROM orders WHERE user_id IN (SELECT user_id FROM users WHERE email = :email)", email);
        jdbc.update("""
                DELETE FROM cart_items WHERE cart_id IN
                  (SELECT cart_id FROM carts WHERE user_id IN (SELECT user_id FROM users WHERE email = :email))
                """, email);
        String cartUpdate = hasCartVersion()
                ? "UPDATE carts SET version = 0, updated_at = SYSTIMESTAMP WHERE user_id IN "
                  + "(SELECT user_id FROM users WHERE email = :email)"
                : "UPDATE carts SET updated_at = SYSTIMESTAMP WHERE user_id IN "
                  + "(SELECT user_id FROM users WHERE email = :email)";
        jdbc.update(cartUpdate, email);
        jdbc.update("""
                UPDATE product_inventory
                   SET on_hand_quantity = 100, reserved_flash_quantity = 0, version = 0, updated_at = SYSTIMESTAMP
                 WHERE product_id IN (SELECT product_id FROM products WHERE category_id IN
                   (SELECT category_id FROM categories WHERE slug = :slug))
                """, Map.of("slug", names.slug));
    }

    private void cleanupOracle(Names names) {
        if (!exists(names)) return;
        resetOracle(names);
        jdbc.update("DELETE FROM carts WHERE user_id IN (SELECT user_id FROM users WHERE email = :email)",
                Map.of("email", names.email));
        jdbc.update("""
                DELETE FROM product_inventory WHERE product_id IN
                  (SELECT product_id FROM products WHERE category_id IN
                    (SELECT category_id FROM categories WHERE slug = :slug))
                """, Map.of("slug", names.slug));
        jdbc.update("DELETE FROM products WHERE category_id IN "
                + "(SELECT category_id FROM categories WHERE slug = :slug)", Map.of("slug", names.slug));
        jdbc.update("DELETE FROM categories WHERE slug = :slug", Map.of("slug", names.slug));
        jdbc.update("DELETE FROM users WHERE email = :email", Map.of("email", names.email));
    }

    private void resetRedis(Names names) {
        clearRedis(names);
        redis.set(names.redisPrefix + "fixture", "ready");
    }

    private void clearRedis(Names names) {
        ScanParams params = new ScanParams().match(names.redisPrefix + "*").count(100);
        String cursor = ScanParams.SCAN_POINTER_START;
        List<String> keys = new ArrayList<>();
        do {
            ScanResult<String> page = redis.scan(cursor, params);
            keys.addAll(page.getResult());
            cursor = page.getCursor();
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
        if (!keys.isEmpty()) redis.unlink(keys.toArray(String[]::new));
    }

    private boolean exists(Names names) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = :email",
                Map.of("email", names.email), Long.class);
        return count != null && count > 0;
    }

    private boolean hasCartVersion() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_tab_columns
                WHERE table_name = 'CARTS' AND column_name = 'VERSION'
                """, Map.of(), Long.class);
        return count != null && count > 0;
    }

    private long id(String sql, String value) {
        Long result = jdbc.queryForObject(sql, Map.of("value", value), Long.class);
        if (result == null) throw new IllegalStateException("Không tìm thấy ID fixture vừa tạo");
        return result;
    }

    private static Names names(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("ID fixture chỉ gồm a-z, 0-9 và tối đa 20 ký tự");
        }
        return new Names(id, "txlab-" + id + "@shopflash.local", "txlab-" + id, "txlab:" + id + ":");
    }

    private record Names(String id, String email, String slug, String redisPrefix) {}
}
