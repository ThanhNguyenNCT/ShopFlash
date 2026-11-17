package com.shopflash.modules.transaction.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

import com.shopflash.common.security.AuthService;
import com.shopflash.common.security.LoginRequest;
import com.shopflash.modules.transaction.fixture.TransactionFixtures;
import com.shopflash.modules.transaction.dto.CheckoutRequest;
import com.shopflash.modules.transaction.service.CheckoutProbe;
import com.shopflash.modules.transaction.service.OrderCodeGenerator;
import com.shopflash.modules.transaction.service.CheckoutService;
import com.shopflash.modules.transaction.service.CartService;
import com.shopflash.common.web.ApiException;
import com.shopflash.modules.storage.repository.StorageRepository;

import tools.jackson.databind.ObjectMapper;

import redis.clients.jedis.RedisClient;

/** Oracle/Redis thật trên schema Flyway tạm; không bọc test trong @Transactional. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CheckoutOracleIT {
    private static final String FIXTURE = "checkout" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static final String OTHER = "other" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private static final String SECRET = Base64.getEncoder().encodeToString("txcheckout-test-secret-32-byte-key!".getBytes());

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;
    @Autowired DataSource dataSource;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired ControlledProbe probe;
    @Autowired ControlledCodes codes;
    @Autowired CheckoutService checkoutService;
    @Autowired CartService cartService;
    @Autowired StorageRepository storageRepository;

    private TransactionFixtures fixtures;
    private MockMvc mvc;
    private String token;
    private String otherToken;

    @BeforeEach
    void setUp() {
        fixtures = new TransactionFixtures(jdbc, manager, redis);
        probe.reset();
        codes.force(null);
        fixtures.setup(FIXTURE);
        fixtures.setup(OTHER);
        token = auth.login(new LoginRequest(email(), "ShopFlash123!")).accessToken();
        otherToken = auth.login(new LoginRequest("txlab-" + OTHER + "@shopflash.local", "ShopFlash123!")).accessToken();
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
    }

    @AfterEach
    void cleanUp() {
        probe.reset();
        codes.force(null);
        fixtures.cleanup(FIXTURE);
        fixtures.cleanup(OTHER);
    }

    @Test
    void invalidRecipientPhoneReturnsValidationErrorWithoutChangingCartOrStock() throws Exception {
        addItems(1);
        CheckoutRequest valid = request(0, 1);
        CheckoutRequest invalid = new CheckoutRequest(valid.cartVersion(), valid.recipientName(),
                "123abc", valid.shippingAddress(), valid.expectedLines());

        place(UUID.randomUUID().toString(), invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.field").value("recipientPhone"));
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
        assertThat(committedCount("SELECT quantity FROM cart_items WHERE cart_id = ? AND product_id = ?",
                cartId(), productId(1))).isEqualTo(1);
        assertThat(committedCount("SELECT version FROM carts WHERE cart_id = ?", cartId())).isZero();
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(1))).isEqualTo(100);
    }

    @Test
    void previewMissingAndEmptyCartDoesNotWrite() throws Exception {
        long userId = userId();
        mvc.perform(get("/api/checkout/preview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/checkout/preview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartVersion").value(0))
                .andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.warnings[0]").value("CART_EMPTY"));
        jdbc.update("DELETE FROM carts WHERE user_id = :userId", Map.of("userId", userId));
        mvc.perform(get("/api/checkout/preview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartVersion").value(0))
                .andExpect(jsonPath("$.lines.length()").value(0));
        assertThat(committedCount("SELECT COUNT(*) FROM carts WHERE user_id = ?", userId)).isZero();
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void previewUsesCurrentPriceAndAvailableStockWithoutWriting() throws Exception {
        long productId = productId(1);
        long cartId = cartId();
        jdbc.update("""
                INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                VALUES (:cartId, :productId, 3, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("cartId", cartId, "productId", productId));
        jdbc.update("UPDATE products SET base_price = :price WHERE product_id = :productId",
                Map.of("price", new BigDecimal("125.50"), "productId", productId));
        jdbc.update("""
                UPDATE product_inventory SET on_hand_quantity = 4, reserved_flash_quantity = 2
                WHERE product_id = :productId
                """, Map.of("productId", productId));
        mvc.perform(get("/api/checkout/preview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(125.50))
                .andExpect(jsonPath("$.lines[0].availableQuantity").value(2))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(376.50))
                .andExpect(jsonPath("$.warnings[0]").value("OUT_OF_STOCK"));
        assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId)).isEqualTo(1);
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId))
                .isEqualTo(4);
    }

    @Test
    void rejectsEmptyChangedPriceInactiveAndReservedStockBeforeWriting() throws Exception {
        CheckoutRequest one = request(0, 1);
        place(UUID.randomUUID().toString(), one)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CART_EMPTY"));
        addItems(1);
        place(UUID.randomUUID().toString(), request(9, 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CART_CHANGED"));
        place(UUID.randomUUID().toString(), request(0, 2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CART_CHANGED"));
        jdbc.update("UPDATE products SET base_price = 125 WHERE product_id = :id", Map.of("id", productId(1)));
        place(UUID.randomUUID().toString(), request(0, 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PRICE_CHANGED"));
        jdbc.update("UPDATE products SET base_price = 100, status = 'INACTIVE' WHERE product_id = :id",
                Map.of("id", productId(1)));
        place(UUID.randomUUID().toString(), request(0, 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PRODUCT_UNAVAILABLE"));
        jdbc.update("UPDATE products SET status = 'ACTIVE' WHERE product_id = :id", Map.of("id", productId(1)));
        jdbc.update("""
                UPDATE product_inventory SET on_hand_quantity = 3, reserved_flash_quantity = 3
                WHERE product_id = :id
                """, Map.of("id", productId(1)));
        place(UUID.randomUUID().toString(), request(0, 1))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("OUT_OF_STOCK"));
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
        assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isEqualTo(1);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(3);
    }

    @Test
    void commitsOneAndFiveLinesAtomically() throws Exception {
        for (int size : List.of(1, 5)) {
            fixtures.reset(FIXTURE);
            int[] numbers = java.util.stream.IntStream.rangeClosed(1, size).toArray();
            addItems(numbers);
            place(UUID.randomUUID().toString(), request(0, numbers))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.totalAmount").value(size * 100.00));
            assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
            assertThat(committedMoney("SELECT total_amount FROM orders WHERE user_id = ?", userId()))
                    .isEqualByComparingTo(new BigDecimal(size * 100 + ".00"));
            assertThat(committedMoney("""
                    SELECT SUM(line_total) FROM order_items WHERE order_id IN
                    (SELECT order_id FROM orders WHERE user_id = ?)
                    """, userId())).isEqualByComparingTo(new BigDecimal(size * 100 + ".00"));
            assertThat(committedCount("""
                    SELECT COUNT(*) FROM order_items WHERE order_id IN
                    (SELECT order_id FROM orders WHERE user_id = ?)
                    """, userId())).isEqualTo(size);
            assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isZero();
            assertThat(committedCount("SELECT version FROM carts WHERE cart_id = ?", cartId())).isEqualTo(1);
            for (int number : numbers) {
                assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                        productId(number))).isEqualTo(99);
                assertThat(committedCount("SELECT version FROM product_inventory WHERE product_id = ?",
                        productId(number))).isEqualTo(1);
            }
        }
    }

    @Test
    void checkoutSelectedLinesKeepsUnselectedCartAndInventory() throws Exception {
        addItems(1, 2, 3);
        jdbc.update("UPDATE products SET status = 'INACTIVE' WHERE product_id = :id", Map.of("id", productId(2)));
        String key = UUID.randomUUID().toString();
        CheckoutRequest selected = request(0, 1, 3);
        place(key, selected).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(200.00));

        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
        assertThat(committedCount("""
                SELECT COUNT(*) FROM order_items WHERE order_id IN
                (SELECT order_id FROM orders WHERE user_id = ?)
                """, userId())).isEqualTo(2);
        assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isEqualTo(1);
        assertThat(committedCount("SELECT quantity FROM cart_items WHERE cart_id = ? AND product_id = ?",
                cartId(), productId(2))).isEqualTo(1);
        assertThat(committedCount("SELECT version FROM carts WHERE cart_id = ?", cartId())).isEqualTo(1);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(1))).isEqualTo(99);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(2))).isEqualTo(100);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                productId(3))).isEqualTo(99);

        place(key, selected).andExpect(status().isOk());
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
        assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isEqualTo(1);
    }

    @Test
    void reusesNormalizedRequestAndRejectsChangedContentOrAnotherCustomer() throws Exception {
        addItems(1, 2);
        String key = UUID.randomUUID().toString();
        CheckoutRequest first = request(0, 1, 2);
        String originalResponse = place(key.toUpperCase(java.util.Locale.ROOT), first)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        CheckoutRequest sameNormalized = new CheckoutRequest(0, "  Khách Thử  ", "0900000000",
                "Địa chỉ thử nghiệm", List.of(
                new CheckoutRequest.ExpectedLine(productId(2), 1, new BigDecimal("100.0")),
                new CheckoutRequest.ExpectedLine(productId(1), 1, new BigDecimal("100"))));
        String repeatedResponse = place(key, sameNormalized)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(repeatedResponse).isEqualTo(originalResponse);
        CheckoutRequest changed = new CheckoutRequest(0, "Người khác", first.recipientPhone(),
                first.shippingAddress(), first.expectedLines());
        place(key, changed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        String foreignResponse = mvc.perform(post("/api/orders").header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(first)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"))
                .andReturn().getResponse().getContentAsString();
        assertThat(foreignResponse).doesNotContain("orderId", "orderCode", "SF-");
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(99);
    }

    @Test
    void concurrentSameAndDifferentKeysNeverDoubleSpend() throws Exception {
        for (boolean sameKey : List.of(true, false)) {
            fixtures.reset(FIXTURE);
            addItems(1);
            String firstKey = UUID.randomUUID().toString();
            String secondKey = sameKey ? firstKey : UUID.randomUUID().toString();
            CheckoutRequest body = request(0, 1);
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondAtLock = new CountDownLatch(1);
            AtomicInteger entered = new AtomicInteger();
            AtomicBoolean stopped = new AtomicBoolean();
            probe.beforeCart.set(() -> { if (entered.incrementAndGet() == 2) secondAtLock.countDown(); });
            probe.afterCart.set(() -> {
                if (stopped.compareAndSet(false, true)) {
                    firstLocked.countDown();
                    await(releaseFirst);
                }
            });
            var pool = Executors.newFixedThreadPool(2);
            try {
                var first = pool.submit(() -> result(firstKey, userId(), body));
                assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var second = pool.submit(() -> result(secondKey, userId(), body));
                assertThat(secondAtLock.await(5, TimeUnit.SECONDS)).isTrue();
                releaseFirst.countDown();
                String firstResult = first.get(10, TimeUnit.SECONDS);
                String secondResult = second.get(10, TimeUnit.SECONDS);
                assertThat(firstResult).startsWith("OK:");
                if (sameKey) assertThat(secondResult).isEqualTo(firstResult);
                else assertThat(secondResult).isEqualTo("CART_CHANGED");
            } finally {
                releaseFirst.countDown();
                pool.shutdownNow();
                probe.reset();
            }
            assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
            assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                    .isEqualTo(99);
        }
    }

    @Test
    void orderCodeCollisionRollsBackAndSameKeyCanRetryWithNewCode() throws Exception {
        addItems(1);
        String collision = "SF-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
                INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount,
                                    total_amount, recipient_name, recipient_phone, shipping_address,
                                    idempotency_key, created_at, updated_at)
                VALUES (:code, :userId, 'NORMAL', 'PENDING', 0, 0, 0, 'Fixture', '0900000000',
                        'Fixture', :key, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("code", collision, "userId", userId(), "key", UUID.randomUUID().toString()));
        String key = UUID.randomUUID().toString();
        codes.force(collision);
        assertThatThrownBy(() -> checkoutService.place(userId(), key, request(0, 1)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("ORDER_CODE_CONFLICT"));
        assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isEqualTo(1);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(100);
        codes.force(null);
        var placed = checkoutService.place(userId(), key, request(0, 1));
        assertThat(placed.orderCode()).isNotEqualTo(collision);
        // Mô phỏng mất phản hồi sau COMMIT: client chỉ còn khóa/body cũ và gửi lại.
        assertThat(checkoutService.place(userId(), key, request(0, 1))).isEqualTo(placed);
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(2);
    }

    @Test
    void concurrentForeignCustomerWithSameKeyGetsOnlyConflict() throws Exception {
        addItems(1);
        long otherUser = userId(OTHER);
        long otherProduct = productId(OTHER, 1);
        long otherCart = jdbc.queryForObject("SELECT cart_id FROM carts WHERE user_id = :id",
                Map.of("id", otherUser), Long.class);
        jdbc.update("""
                INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                VALUES (:cart, :product, 1, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("cart", otherCart, "product", otherProduct));
        String key = UUID.randomUUID().toString();
        CheckoutRequest otherBody = new CheckoutRequest(0, "Khách Thử", "0900000000", "Địa chỉ thử nghiệm",
                List.of(new CheckoutRequest.ExpectedLine(otherProduct, 1, new BigDecimal("100.00"))));
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        probe.beforeInsert.set(() -> { bothReady.countDown(); await(release); });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> result(key, userId(), request(0, 1)));
            var second = pool.submit(() -> result(key, otherUser, otherBody));
            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            List<String> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(s -> s.startsWith("OK:")).count()).isEqualTo(1);
            assertThat(outcomes).contains("IDEMPOTENCY_CONFLICT");
        } finally {
            release.countDown();
            pool.shutdownNow();
            probe.reset();
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE idempotency_key = :key",
                Map.of("key", key), Long.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void lockTimeoutRollsBackAndSameKeyRetriesSafely() throws Exception {
        addItems(1);
        String key = UUID.randomUUID().toString();
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try {
                try (PreparedStatement lock = holder.prepareStatement(
                        "UPDATE carts SET updated_at = updated_at WHERE cart_id = ?")) {
                    lock.setLong(1, cartId());
                    assertThat(lock.executeUpdate()).isEqualTo(1);
                }
                assertThatThrownBy(() -> checkoutService.place(userId(), key, request(0, 1)))
                        .isInstanceOf(ApiException.class)
                        .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("LOCK_TIMEOUT"));
            } finally {
                holder.rollback();
            }
        }
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(100);
        var placed = checkoutService.place(userId(), key, request(0, 1));
        assertThat(checkoutService.place(userId(), key, request(0, 1))).isEqualTo(placed);
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
    }

    @Test
    void boundedCheckoutLockWaitRollsBackAndReplayNeverDuplicates() throws Exception {
        addItems(1);
        String key = UUID.randomUUID().toString();
        CountDownLatch firstLocks = new CountDownLatch(2);
        CountDownLatch continueSecondLock = new CountDownLatch(1);
        probe.afterCart.set(() -> { firstLocks.countDown(); await(continueSecondLock); });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var checkoutFuture = pool.submit(() -> result(key, userId(), request(0, 1)));
            var otherFuture = pool.submit(() -> {
                try (Connection other = dataSource.getConnection()) {
                    other.setAutoCommit(false);
                    try {
                        try (PreparedStatement first = other.prepareStatement(
                                "UPDATE products SET updated_at = updated_at WHERE product_id = ?")) {
                            first.setLong(1, productId(1));
                            assertThat(first.executeUpdate()).isEqualTo(1);
                        }
                        firstLocks.countDown();
                        await(continueSecondLock);
                        try (PreparedStatement second = other.prepareStatement(
                                "UPDATE carts SET updated_at = updated_at WHERE cart_id = ?")) {
                            second.setLong(1, cartId());
                            second.executeUpdate();
                            return "OK";
                        }
                    } catch (SQLException e) {
                        return "ORA-" + e.getErrorCode();
                    } finally {
                        other.rollback();
                    }
                }
            });
            assertThat(firstLocks.await(5, TimeUnit.SECONDS)).isTrue();
            continueSecondLock.countDown();
            String checkoutOutcome = checkoutFuture.get(10, TimeUnit.SECONDS);
            String otherOutcome = otherFuture.get(10, TimeUnit.SECONDS);
            assertThat(List.of(checkoutOutcome, otherOutcome))
                    .containsAnyOf("DEADLOCK", "ORA-60", "LOCK_TIMEOUT", "ORA-30006");
            if (checkoutOutcome.equals("DEADLOCK") || checkoutOutcome.equals("LOCK_TIMEOUT")) {
                assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
                assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                        .isEqualTo(100);
            }
            var placed = checkoutService.place(userId(), key, request(0, 1));
            assertThat(checkoutService.place(userId(), key, request(0, 1))).isEqualTo(placed);
            assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
            assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                    .isEqualTo(99);
        } finally {
            continueSecondLock.countDown();
            pool.shutdownNow();
            probe.reset();
        }
    }

    @Test
    void oracleDeadlockWithTwoConnectionsHasOneVictimAndNoCommittedWrites() throws Exception {
        try (Connection cartFirst = dataSource.getConnection();
             Connection productFirst = dataSource.getConnection()) {
            cartFirst.setAutoCommit(false);
            productFirst.setAutoCommit(false);
            try (PreparedStatement a = cartFirst.prepareStatement(
                         "UPDATE carts SET updated_at = updated_at WHERE cart_id = ?");
                 PreparedStatement b = productFirst.prepareStatement(
                         "UPDATE products SET updated_at = updated_at WHERE product_id = ?")) {
                a.setLong(1, cartId());
                b.setLong(1, productId(1));
                assertThat(a.executeUpdate()).isEqualTo(1);
                assertThat(b.executeUpdate()).isEqualTo(1);
            }
            CountDownLatch bothReady = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var a = pool.submit(() -> secondLock(cartFirst,
                        "UPDATE products SET updated_at = updated_at WHERE product_id = ?", productId(1),
                        bothReady, start));
                var b = pool.submit(() -> secondLock(productFirst,
                        "UPDATE carts SET updated_at = updated_at WHERE cart_id = ?", cartId(),
                        bothReady, start));
                assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).contains("ORA-60");
            } finally {
                start.countDown();
                cartFirst.rollback();
                productFirst.rollback();
                pool.shutdownNow();
            }
        }
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
        assertThat(committedCount("SELECT version FROM carts WHERE cart_id = ?", cartId())).isZero();
    }

    @Test
    void injectedFailuresAfterPartialWritesRollbackEveryTable() throws Exception {
        for (AtomicReference<Runnable> stage : List.of(
                probe.afterOrder, probe.afterItems, probe.afterInventory, probe.beforeClear)) {
            fixtures.reset(FIXTURE);
            addItems(1, 2);
            stage.set(() -> { throw new IllegalStateException("Lỗi thử nghiệm sau ghi một phần"); });
            String key = UUID.randomUUID().toString();
            assertThatThrownBy(() -> checkoutService.place(userId(), key, request(0, 1, 2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Lỗi thử nghiệm");
            stage.set(null);
            assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
            assertThat(committedCount("""
                    SELECT COUNT(*) FROM order_items WHERE order_id IN
                    (SELECT order_id FROM orders WHERE user_id = ?)
                    """, userId())).isZero();
            assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isEqualTo(2);
            assertThat(committedCount("SELECT version FROM carts WHERE cart_id = ?", cartId())).isZero();
            for (int number : List.of(1, 2)) {
                assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                        productId(number))).isEqualTo(100);
                assertThat(committedCount("SELECT version FROM product_inventory WHERE product_id = ?",
                        productId(number))).isZero();
            }
            // Sau rollback, cùng khóa và body cũ có thể được thực hiện lại từ đầu.
            assertThat(checkoutService.place(userId(), key, request(0, 1, 2)).status()).isEqualTo("PENDING");
        }
    }

    @Test
    void checkoutThenCartUpdateRejectsStaleVersionWithoutLosingOrder() throws Exception {
        addItems(1);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cartStarted = new CountDownLatch(1);
        probe.afterCart.set(() -> { locked.countDown(); await(release); });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var order = pool.submit(() -> result(UUID.randomUUID().toString(), userId(), request(0, 1)));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var cart = pool.submit(() -> {
                cartStarted.countDown();
                try {
                    cartService.setQuantity(userId(), 0, productId(1), 2);
                    return "OK";
                } catch (ApiException e) {
                    return e.code();
                }
            });
            assertThat(cartStarted.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(order.get(10, TimeUnit.SECONDS)).startsWith("OK:");
            assertThat(cart.get(10, TimeUnit.SECONDS)).isEqualTo("CART_CHANGED");
        } finally {
            release.countDown();
            pool.shutdownNow();
            probe.reset();
        }
        assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isZero();
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isEqualTo(1);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(99);
    }

    @Test
    void f2InventoryUpdateBeforeCheckoutInventoryLockMakesCheckoutReject() throws Exception {
        addItems(1);
        CountDownLatch productLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        probe.afterProduct.set(() -> { productLocked.countDown(); await(release); });
        var pool = Executors.newSingleThreadExecutor();
        try {
            var order = pool.submit(() -> result(UUID.randomUUID().toString(), userId(), request(0, 1)));
            assertThat(productLocked.await(5, TimeUnit.SECONDS)).isTrue();
            // Phương thức UPDATE có version của F2; Checkout chưa khóa Inventory.
            assertThat(storageRepository.updateInventory(productId(1), 0, 0)).isEqualTo(1);
            release.countDown();
            assertThat(order.get(10, TimeUnit.SECONDS)).isEqualTo("OUT_OF_STOCK");
        } finally {
            release.countDown();
            pool.shutdownNow();
            probe.reset();
        }
        assertThat(committedCount("SELECT COUNT(*) FROM orders WHERE user_id = ?", userId())).isZero();
        assertThat(committedCount("SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId())).isEqualTo(1);
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                .isZero();
        assertThat(committedCount("SELECT version FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(1);
    }

    @Test
    void checkoutInventoryWriteBeforeF2UpdateMakesF2VersionStale() throws Exception {
        addItems(1);
        CountDownLatch inventoryWritten = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch f2Started = new CountDownLatch(1);
        probe.afterInventory.set(() -> { inventoryWritten.countDown(); await(release); });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var order = pool.submit(() -> result(UUID.randomUUID().toString(), userId(), request(0, 1)));
            assertThat(inventoryWritten.await(5, TimeUnit.SECONDS)).isTrue();
            var f2 = pool.submit(() -> {
                f2Started.countDown();
                return storageRepository.updateInventory(productId(1), 50, 0);
            });
            assertThat(f2Started.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(order.get(10, TimeUnit.SECONDS)).startsWith("OK:");
            assertThat(f2.get(10, TimeUnit.SECONDS)).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
            probe.reset();
        }
        assertThat(committedCount("SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(99);
        assertThat(committedCount("SELECT version FROM product_inventory WHERE product_id = ?", productId(1)))
                .isEqualTo(1);
    }

    private static String secondLock(Connection connection, String sql, long id,
                                     CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        await(start);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
            return "OK";
        } catch (SQLException e) {
            connection.rollback();
            return "ORA-" + e.getErrorCode();
        }
    }

    private String result(String key, long user, CheckoutRequest body) {
        try {
            return "OK:" + checkoutService.place(user, key, body).orderId();
        } catch (ApiException e) {
            return e.code();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Hết thời gian chờ điểm đồng bộ");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bài test bị ngắt", e);
        }
    }

    private org.springframework.test.web.servlet.ResultActions place(String key, CheckoutRequest request) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)));
    }

    private CheckoutRequest request(long version, int... numbers) {
        List<CheckoutRequest.ExpectedLine> lines = Arrays.stream(numbers)
                .mapToObj(number -> new CheckoutRequest.ExpectedLine(productId(number), 1, new BigDecimal("100.00")))
                .toList();
        return new CheckoutRequest(version, "Khách Thử", "0900000000", "Địa chỉ thử nghiệm", lines);
    }

    private void addItems(int... numbers) {
        for (int number : numbers) {
            jdbc.update("""
                    INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                    VALUES (:cartId, :productId, 1, SYSTIMESTAMP, SYSTIMESTAMP)
                    """, Map.of("cartId", cartId(), "productId", productId(number)));
        }
    }

    private long committedCount(String sql, long... ids) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) statement.setLong(i + 1, ids[i]);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private BigDecimal committedMoney(String sql, long id) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private long userId() {
        return userId(FIXTURE);
    }

    private long userId(String fixture) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = :email",
                Map.of("email", "txlab-" + fixture + "@shopflash.local"), Long.class);
    }

    private long cartId() {
        return jdbc.queryForObject("SELECT cart_id FROM carts WHERE user_id = :userId", Map.of("userId", userId()), Long.class);
    }

    private long productId(int number) {
        return productId(FIXTURE, number);
    }

    private long productId(String fixture, int number) {
        return jdbc.queryForObject("SELECT product_id FROM products WHERE sku = :sku",
                Map.of("sku", "TXLAB-" + fixture.toUpperCase() + "-" + number), Long.class);
    }

    private static String email() {
        return "txlab-" + FIXTURE + "@shopflash.local";
    }

    @TestConfiguration
    static class Controls {
        @Bean ControlledProbe checkoutProbe() { return new ControlledProbe(); }
        @Bean @Primary ControlledCodes controlledCodes() { return new ControlledCodes(); }
    }

    static class ControlledProbe implements CheckoutProbe {
        final AtomicReference<Runnable> beforeCart = new AtomicReference<>();
        final AtomicReference<Runnable> afterCart = new AtomicReference<>();
        final AtomicReference<Runnable> afterProduct = new AtomicReference<>();
        final AtomicReference<Runnable> beforeInsert = new AtomicReference<>();
        final AtomicReference<Runnable> afterOrder = new AtomicReference<>();
        final AtomicReference<Runnable> afterItems = new AtomicReference<>();
        final AtomicReference<Runnable> afterInventory = new AtomicReference<>();
        final AtomicReference<Runnable> beforeClear = new AtomicReference<>();
        void reset() {
            beforeCart.set(null); afterCart.set(null); afterProduct.set(null);
            beforeInsert.set(null); afterOrder.set(null); afterItems.set(null);
            afterInventory.set(null); beforeClear.set(null);
        }
        private static void run(AtomicReference<Runnable> hook) {
            Runnable action = hook.get();
            if (action != null) action.run();
        }
        @Override public void beforeCartLock() { run(beforeCart); }
        @Override public void afterCartLock() { run(afterCart); }
        @Override public void afterProductLock() { run(afterProduct); }
        @Override public void beforeOrderInsert() { run(beforeInsert); }
        @Override public void afterOrderWrite() { run(afterOrder); }
        @Override public void afterOrderItemsWrite() { run(afterItems); }
        @Override public void afterInventoryWrite() { run(afterInventory); }
        @Override public void beforeCartClear() { run(beforeClear); }
    }

    static class ControlledCodes extends OrderCodeGenerator {
        private final AtomicReference<String> forced = new AtomicReference<>();
        void force(String code) { forced.set(code); }
        @Override public String next() {
            String code = forced.get();
            return code == null ? super.next() : code;
        }
    }
}
