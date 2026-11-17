package com.shopflash.modules.transaction.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

import com.shopflash.common.security.AuthService;
import com.shopflash.common.security.LoginRequest;
import com.shopflash.modules.transaction.fixture.TransactionFixtures;
import com.shopflash.modules.transaction.service.OrderCancellationProbe;
import com.shopflash.modules.transaction.service.OrderService;

import redis.clients.jedis.RedisClient;

/** Kiểm tra F8 trên Oracle thật; hậu kiểm dùng kết nối khác sau COMMIT. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OrderOracleIT {
    private static final String FIRST = "orders" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static final String SECOND = "others" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static final String SECRET = Base64.getEncoder().encodeToString("tx-orders-secret-must-be-32-bytes!!".getBytes());

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;
    @Autowired DataSource dataSource;
    @Autowired AuthService auth;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired OrderService orders;
    @Autowired ControlledCancellationProbe probe;

    private TransactionFixtures fixtures;
    private MockMvc mvc;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        fixtures = new TransactionFixtures(jdbc, manager, redis);
        fixtures.setup(FIRST);
        fixtures.setup(SECOND);
        probe.reset();
        token = auth.login(new LoginRequest(email(FIRST), "ShopFlash123!")).accessToken();
        otherToken = auth.login(new LoginRequest(email(SECOND), "ShopFlash123!")).accessToken();
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
    }

    @AfterEach
    void cleanUp() {
        probe.reset();
        fixtures.cleanup(FIRST);
        fixtures.cleanup(SECOND);
    }

    @Test
    void listIsScopedSortedFilteredAndPaged() throws Exception {
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items.length()").value(0));
        long oldest = insertOrder(FIRST, "NORMAL", "PENDING", 100);
        long middle = insertOrder(FIRST, "NORMAL", "CONFIRMED", 50);
        long newest = insertOrder(FIRST, "NORMAL", "CANCELLED", 0);
        insertOrder(SECOND, "NORMAL", "PENDING", 0);
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].orderId").value(newest))
                .andExpect(jsonPath("$.items[1].orderId").value(middle));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("page", "2").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].orderId").value(oldest));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("page", "3").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token)
                        .param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].orderId").value(oldest));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        assertThat(committedLong("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId(FIRST))).isEqualTo(3);
    }

    @Test
    void equalCreationTimeUsesDescendingOrderId() throws Exception {
        long first = insertOrder(FIRST, "NORMAL", "PENDING", 0);
        long second = insertOrder(FIRST, "NORMAL", "PENDING", 0);
        jdbc.update("""
                UPDATE orders SET created_at = TO_TIMESTAMP_TZ('2026-10-26T12:00:00 +07:00',
                  'YYYY-MM-DD"T"HH24:MI:SS TZH:TZM') WHERE order_id IN (:first, :second)
                """, new MapSqlParameterSource().addValue("first", first).addValue("second", second));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].orderId").value(second))
                .andExpect(jsonPath("$.items[1].orderId").value(first));
    }

    @Test
    void invalidPagingAndStatusAreRejected() throws Exception {
        for (String page : new String[] {"0", "-1", "abc"}) {
            mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token).param("page", page))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        for (String size : new String[] {"0", "101", "abc"}) {
            mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token).param("size", size))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token).param("status", "PAID"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void detailUsesPurchaseSnapshotAndHidesAnotherCustomersOrder() throws Exception {
        long own = insertOrder(FIRST, "NORMAL", "PENDING", 0);
        long foreign = insertOrder(SECOND, "NORMAL", "PENDING", 0);
        long productId = productId(FIRST, 1);
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                                         unit_price, quantity, line_total)
                VALUES (:orderId, :productId, 'Tên lúc mua', 'SKU-LUC-MUA', 123.45, 2, 246.90)
                """, Map.of("orderId", own, "productId", productId));
        jdbc.update("""
                UPDATE products SET name = 'Tên hiện tại', base_price = 999, status = 'INACTIVE',
                                    thumbnail_url = :image WHERE product_id = :id
                """, Map.of("id", productId, "image", "https://example.test/order-product.png"));
        mvc.perform(get("/api/orders/{id}", own).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productName").value("Tên lúc mua"))
                .andExpect(jsonPath("$.items[0].productSku").value("SKU-LUC-MUA"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(123.45))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://example.test/order-product.png"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        jdbc.update("UPDATE products SET thumbnail_url = NULL WHERE product_id = :id", Map.of("id", productId));
        mvc.perform(get("/api/orders/{id}", own).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").isEmpty());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].itemCount").value(2));
        for (long id : new long[] {foreign, Long.MAX_VALUE}) {
            mvc.perform(get("/api/orders/{id}", id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
        }
        assertThat(committedLong("SELECT COUNT(*) FROM order_items WHERE order_id = ?", own)).isEqualTo(1);
    }

    @Test
    void cancelCommitsOnceAndNeverRestoresCart() throws Exception {
        long orderId = insertOrder(FIRST, "NORMAL", "PENDING", 0);
        addOrderItem(orderId, 1);
        addOrderItem(orderId, 2);
        mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.items.length()").value(2));
        assertThat(committedLong("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(FIRST, 1))).isEqualTo(101);
        assertThat(committedLong("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(FIRST, 2))).isEqualTo(101);
        assertThat(committedLong("SELECT version FROM product_inventory WHERE product_id = ?",
                productId(FIRST, 1))).isEqualTo(1);
        assertThat(committedLong("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId(FIRST))).isZero();
        mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ORDER_NOT_CANCELLABLE"));
        assertThat(committedLong("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(FIRST, 1))).isEqualTo(101);
    }

    @Test
    void rejectsForeignFlashSaleAndNonPendingOrders() throws Exception {
        long foreign = insertOrder(SECOND, "NORMAL", "PENDING", 0);
        long flash = insertOrder(FIRST, "FLASH_SALE", "PENDING", 0);
        mvc.perform(post("/api/orders/{id}/cancel", foreign).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ORDER_NOT_FOUND"));
        mvc.perform(post("/api/orders/{id}/cancel", flash).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ORDER_NOT_CANCELLABLE"));
        for (String state : new String[] {"CONFIRMED", "COMPLETED", "CANCELLED"}) {
            long orderId = insertOrder(FIRST, "NORMAL", state, 0);
            mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ORDER_NOT_CANCELLABLE"));
        }
        assertThat(committedLong("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(FIRST, 1))).isEqualTo(100);
        mvc.perform(post("/api/orders/{id}/cancel", foreign)).andExpect(status().isUnauthorized());
    }

    @Test
    void partialInventoryRestorationRollsBackOrderAndAllStock() throws Exception {
        long orderId = insertOrder(FIRST, "NORMAL", "PENDING", 0);
        addOrderItem(orderId, 1);
        addOrderItem(orderId, 2);
        probe.afterRestore.set(productId -> {
            if (productId == productId(FIRST, 1)) throw new IllegalStateException("Lỗi thử nghiệm sau hoàn kho đầu");
        });
        assertThatThrownBy(() -> orders.cancel(userId(FIRST), orderId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Lỗi thử nghiệm");
        probe.reset();
        assertThat(committedString("SELECT status FROM orders WHERE order_id = ?", orderId)).isEqualTo("PENDING");
        assertThat(committedLong("SELECT COUNT(*) FROM orders WHERE order_id = ? AND cancelled_at IS NOT NULL", orderId))
                .isZero();
        for (int product : new int[] {1, 2}) {
            assertThat(committedLong("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                    productId(FIRST, product))).isEqualTo(100);
            assertThat(committedLong("SELECT version FROM product_inventory WHERE product_id = ?",
                    productId(FIRST, product))).isZero();
        }
        assertThat(orders.cancel(userId(FIRST), orderId).status()).isEqualTo("CANCELLED");
    }

    @Test
    void concurrentCancellationRestoresStockOnlyOnce() throws Exception {
        long orderId = insertOrder(FIRST, "NORMAL", "PENDING", 0);
        addOrderItem(orderId, 1);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        probe.beforeLock.set(() -> {
            if (Thread.currentThread().getName().contains("second")) secondEntered.countDown();
        });
        probe.afterLock.set(() -> {
            if (Thread.currentThread().getName().contains("first")) {
                firstLocked.countDown();
                await(releaseFirst);
            }
        });
        var pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(poolName.getAndSet("second"));
            return thread;
        });
        try {
            var first = pool.submit(() -> cancelResult(orderId));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> cancelResult(orderId));
            assertThat(secondEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("CANCELLED");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("ORDER_NOT_CANCELLABLE");
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            probe.reset();
        }
        assertThat(committedLong("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(FIRST, 1))).isEqualTo(101);
        assertThat(committedLong("SELECT version FROM product_inventory WHERE product_id = ?",
                productId(FIRST, 1))).isEqualTo(1);
    }

    @Test
    void orderLockTimeoutLeavesOrderAndInventoryUnchanged() throws Exception {
        long orderId = insertOrder(FIRST, "NORMAL", "PENDING", 0);
        addOrderItem(orderId, 1);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try {
                try (PreparedStatement lock = holder.prepareStatement("SELECT status FROM orders WHERE order_id = ? FOR UPDATE")) {
                    lock.setLong(1, orderId);
                    try (var rows = lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                mvc.perform(post("/api/orders/{id}/cancel", orderId).header("Authorization", "Bearer " + token))
                        .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LOCK_TIMEOUT"));
                assertThat(committedString("SELECT status FROM orders WHERE order_id = ?", orderId)).isEqualTo("PENDING");
                assertThat(committedLong("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                        productId(FIRST, 1))).isEqualTo(100);
            } finally {
                holder.rollback();
            }
        }
    }

    private final AtomicReference<String> poolName = new AtomicReference<>("first");

    private String cancelResult(long orderId) {
        try {
            return orders.cancel(userId(FIRST), orderId).status();
        } catch (com.shopflash.common.web.ApiException error) {
            return error.code();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Hết thời gian chờ điểm đồng bộ");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bài test bị ngắt", error);
        }
    }

    private void addOrderItem(long orderId, int product) {
        jdbc.update("""
                INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                                         unit_price, quantity, line_total)
                VALUES (:orderId, :productId, 'Tên lúc mua', 'TX-TEST', 123.45, 1, 123.45)
                """, Map.of("orderId", orderId, "productId", productId(FIRST, product)));
    }

    private long cartId(String fixture) {
        return jdbc.queryForObject("SELECT cart_id FROM carts WHERE user_id = :id",
                Map.of("id", userId(fixture)), Long.class);
    }

    private String committedString(String sql, long id) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private long insertOrder(String fixture, String type, String status, int ageSeconds) {
        String key = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount,
                                    total_amount, recipient_name, recipient_phone, shipping_address,
                                    idempotency_key, created_at, updated_at, cancelled_at)
                VALUES (:code, :userId, :type, :status, 246.90, 0, 246.90,
                        'Người nhận', '0900000000', 'Địa chỉ', :key,
                        SYSTIMESTAMP - NUMTODSINTERVAL(:age, 'SECOND'), SYSTIMESTAMP,
                        CASE WHEN :status = 'CANCELLED' THEN SYSTIMESTAMP ELSE NULL END)
                """, new MapSqlParameterSource().addValue("code", "TEST-" + key.substring(0, 12))
                .addValue("userId", userId(fixture)).addValue("type", type).addValue("status", status)
                .addValue("key", key).addValue("age", ageSeconds));
        return jdbc.queryForObject("SELECT order_id FROM orders WHERE idempotency_key = :key",
                Map.of("key", key), Long.class);
    }

    private long committedLong(String sql, long id) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private long userId(String fixture) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = :email",
                Map.of("email", email(fixture)), Long.class);
    }

    private long productId(String fixture, int number) {
        return jdbc.queryForObject("SELECT product_id FROM products WHERE sku = :sku",
                Map.of("sku", "TXLAB-" + fixture.toUpperCase() + "-" + number), Long.class);
    }

    private static String email(String fixture) {
        return "txlab-" + fixture + "@shopflash.local";
    }

    @TestConfiguration
    static class Controls {
        @Bean ControlledCancellationProbe cancellationProbe() { return new ControlledCancellationProbe(); }
    }

    static class ControlledCancellationProbe implements OrderCancellationProbe {
        final AtomicReference<Runnable> beforeLock = new AtomicReference<>();
        final AtomicReference<Runnable> afterLock = new AtomicReference<>();
        final AtomicReference<java.util.function.LongConsumer> afterRestore = new AtomicReference<>();
        void reset() { beforeLock.set(null); afterLock.set(null); afterRestore.set(null); }
        @Override public void beforeOrderLock() {
            Runnable hook = beforeLock.get();
            if (hook != null) hook.run();
        }
        @Override public void afterOrderLock() {
            Runnable hook = afterLock.get();
            if (hook != null) hook.run();
        }
        @Override public void afterInventoryRestore(long productId) {
            java.util.function.LongConsumer hook = afterRestore.get();
            if (hook != null) hook.accept(productId);
        }
    }
}
