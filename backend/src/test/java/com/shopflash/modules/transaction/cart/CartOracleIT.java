package com.shopflash.modules.transaction.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.Filter;

import com.shopflash.common.security.AuthService;
import com.shopflash.common.security.LoginRequest;
import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.controller.CartController;
import com.shopflash.modules.transaction.fixture.TransactionFixtures;
import com.shopflash.modules.transaction.service.CartService;

import redis.clients.jedis.RedisClient;

/** Chạy với Oracle/Redis thật trên schema Flyway V2; không có @Transactional bao bài test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class CartOracleIT {
    private static final String FIRST = fixtureId();
    private static final String SECOND = fixtureId();
    private static final String SECRET = Base64.getEncoder().encodeToString("txcart-test-secret-32-byte-random-key!".getBytes());

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;
    @Autowired DataSource dataSource;
    @Autowired CartController controller;
    @Autowired CartService service;
    @Autowired AuthService auth;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;

    private TransactionFixtures fixtures;
    private MockMvc mvc;
    private String firstToken;
    private String secondToken;

    @BeforeEach
    void setUp() {
        fixtures = new TransactionFixtures(jdbc, manager, redis);
        fixtures.setup(FIRST);
        fixtures.setup(SECOND);
        firstToken = auth.login(new LoginRequest(email(FIRST), "ShopFlash123!")).accessToken();
        secondToken = auth.login(new LoginRequest(email(SECOND), "ShopFlash123!")).accessToken();
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
    }

    @AfterEach
    void cleanUp() {
        fixtures.cleanup(FIRST);
        fixtures.cleanup(SECOND);
    }

    @Test
    void missingCartReturnsEmptyWithoutCreatingOracleRow() throws Exception {
        jdbc.update("DELETE FROM carts WHERE user_id = :userId", Map.of("userId", userId(FIRST)));

        mvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + firstToken)
                        .queryParam("userId", String.valueOf(userId(SECOND))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0.00));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM carts WHERE user_id = ?")) {
            statement.setLong(1, userId(FIRST));
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isZero();
            }
        }
    }

    @Test
    void readsCurrentPricesTotalsVersionAndOnlyOwnItems() throws Exception {
        insertItem(FIRST, 1, 1);
        insertItem(SECOND, 1, 2);
        insertItem(SECOND, 2, 3);
        jdbc.update("UPDATE carts SET version = 4 WHERE user_id = :userId", Map.of("userId", userId(FIRST)));
        jdbc.update("UPDATE carts SET version = 7 WHERE user_id = :userId", Map.of("userId", userId(SECOND)));
        jdbc.update("UPDATE products SET base_price = :price WHERE product_id = :productId",
                Map.of("price", new java.math.BigDecimal("125.50"), "productId", productId(SECOND, 2)));

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.totalAmount").value(100.00));

        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.items[1].quantity").value(3))
                .andExpect(jsonPath("$.items[1].unitPrice").value(125.50))
                .andExpect(jsonPath("$.totalAmount").value(576.50));
    }

    @Test
    void doesNotMixVersionAndItemsAcrossUncommittedChange() throws Exception {
        insertItem(FIRST, 1, 1);
        long cartId = cartId(FIRST);
        try (Connection writer = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            try {
                try (PreparedStatement updateCart = writer.prepareStatement(
                        "UPDATE carts SET version = version + 1 WHERE cart_id = ?");
                     PreparedStatement updateItem = writer.prepareStatement(
                             "UPDATE cart_items SET quantity = 3 WHERE cart_id = ?")) {
                    updateCart.setLong(1, cartId);
                    updateCart.executeUpdate();
                    updateItem.setLong(1, cartId);
                    updateItem.executeUpdate();
                }
                mvc.perform(get("/api/cart").header("Authorization", "Bearer " + firstToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.version").value(0))
                        .andExpect(jsonPath("$.items[0].quantity").value(1));
                writer.commit();
            } finally {
                writer.rollback();
            }
        }
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void addsFirstAndDuplicateItemWithoutReservingStockAndRejectsInvalidProduct() throws Exception {
        long userId = userId(FIRST);
        long productId = productId(FIRST, 1);
        long inactiveProduct = productId(FIRST, 2);
        jdbc.update("DELETE FROM carts WHERE user_id = :userId", Map.of("userId", userId));

        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "0").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
        assertThat(committedQuantity(userId, productId)).isEqualTo(5);
        assertThat(committedStock(productId)).isEqualTo(100);

        jdbc.update("UPDATE products SET status = 'INACTIVE' WHERE product_id = :id", Map.of("id", inactiveProduct));
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + inactiveProduct + ",\"quantity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PRODUCT_UNAVAILABLE"));
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":999999999999,\"quantity\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":1,\"userId\":" + userId(SECOND) + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + productId + ",\"quantity\":995}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE user_id = :id",
                Map.of("id", userId), Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT on_hand_quantity FROM product_inventory WHERE product_id = :id",
                Map.of("id", productId), Long.class)).isEqualTo(100);
    }

    @Test
    void updatesDeletesAndClearsWithoutRemovingCartOrChangingOtherCustomer() throws Exception {
        long firstProduct = productId(FIRST, 1);
        long secondProduct = productId(FIRST, 2);
        insertItem(SECOND, 1, 8);

        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "0").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + firstProduct + ",\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/cart/items/{id}", firstProduct).header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.items[0].quantity").value(4));
        assertThat(committedQuantity(userId(FIRST), firstProduct)).isEqualTo(4);
        assertThat(committedStock(firstProduct)).isEqualTo(100);
        mvc.perform(delete("/api/cart/items/{id}", firstProduct).header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(committedQuantity(userId(FIRST), firstProduct)).isZero();
        assertThat(committedStock(firstProduct)).isEqualTo(100);
        mvc.perform(delete("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        mvc.perform(delete("/api/cart/items/{id}", firstProduct).header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "3"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("CART_ITEM_NOT_FOUND"));
        mvc.perform(patch("/api/cart/items/{id}", firstProduct).header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());

        service.add(userId(FIRST), 3, firstProduct, 1);
        service.add(userId(FIRST), 4, secondProduct, 1);
        mvc.perform(delete("/api/cart/items").header("Authorization", "Bearer " + firstToken)
                        .header("X-Cart-Version", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id = :id",
                Map.of("id", userId(FIRST)), Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id = :id",
                Map.of("id", cartId(SECOND)), Long.class)).isEqualTo(1);
    }

    @Test
    void competingInitialAddsAndStaleEditsHaveOneWinner() throws Exception {
        long userId = userId(FIRST);
        long productId = productId(FIRST, 1);
        jdbc.update("DELETE FROM carts WHERE user_id = :userId", Map.of("userId", userId));

        var results = race(() -> service.add(userId, 0, productId, 1));
        assertThat(results).containsExactlyInAnyOrder("OK", "CART_CHANGED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carts WHERE user_id = :id",
                Map.of("id", userId), Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE cart_id = :id",
                Map.of("id", cartId(FIRST)), Integer.class)).isEqualTo(1);

        results = race(() -> service.setQuantity(userId, 1, productId, 2),
                () -> service.setQuantity(userId, 1, productId, 3));
        assertThat(results).containsExactlyInAnyOrder("OK", "CART_CHANGED");
        assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE user_id = :id",
                Map.of("id", userId), Long.class)).isEqualTo(2);
        int finalQuantity = jdbc.queryForObject("SELECT quantity FROM cart_items WHERE cart_id = :id",
                Map.of("id", cartId(FIRST)), Integer.class);
        assertThat(finalQuantity).isIn(2, 3);
    }

    @Test
    void heldCartLockTimesOutAndRollsBackWithoutChanges() throws Exception {
        long userId = userId(FIRST);
        long productId = productId(FIRST, 1);
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (PreparedStatement lock = holder.prepareStatement("SELECT cart_id FROM carts WHERE user_id = ? FOR UPDATE")) {
                lock.setLong(1, userId);
                try (var rows = lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
            }
            assertThatThrownBy(() -> service.add(userId, 0, productId, 1))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.code()).isEqualTo("LOCK_TIMEOUT"));
            holder.rollback();
        }
        assertThat(jdbc.queryForObject("SELECT version FROM carts WHERE user_id = :id",
                Map.of("id", userId), Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id = :id",
                Map.of("id", cartId(FIRST)), Long.class)).isZero();
    }

    private static java.util.List<String> race(java.util.concurrent.Callable<?> first) throws Exception {
        return race(first, first);
    }

    private static java.util.List<String> race(java.util.concurrent.Callable<?> first,
                                                java.util.concurrent.Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> runRacer(first, ready, start));
            var two = executor.submit(() -> runRacer(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private static String runRacer(java.util.concurrent.Callable<?> operation,
                                   CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Hết thời gian chờ điểm đồng bộ");
        try {
            operation.call();
            return "OK";
        } catch (ApiException e) {
            return e.code();
        }
    }

    private void insertItem(String id, int productNumber, int quantity) {
        jdbc.update("""
                INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                VALUES (:cartId, :productId, :quantity, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("cartId", cartId(id), "productId", productId(id, productNumber), "quantity", quantity));
    }

    private long cartId(String id) {
        return jdbc.queryForObject("""
                SELECT c.cart_id FROM carts c JOIN users u ON u.user_id = c.user_id
                WHERE u.email = :email
                """, Map.of("email", email(id)), Long.class);
    }

    private long userId(String id) {
        return jdbc.queryForObject("SELECT user_id FROM users WHERE email = :email",
                Map.of("email", email(id)), Long.class);
    }

    private long productId(String id, int number) {
        return jdbc.queryForObject("SELECT product_id FROM products WHERE sku = :sku",
                Map.of("sku", "TXLAB-" + id.toUpperCase() + "-" + number), Long.class);
    }

    private int committedQuantity(long userId, long productId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT ci.quantity FROM cart_items ci JOIN carts c ON c.cart_id = ci.cart_id
                     WHERE c.user_id = ? AND ci.product_id = ?
                     """)) {
            statement.setLong(1, userId);
            statement.setLong(2, productId);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        }
    }

    private int committedStock(long productId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?
                     """)) {
            statement.setLong(1, productId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Fixture thiếu tồn kho");
                return rows.getInt(1);
            }
        }
    }

    private static String email(String id) {
        return "txlab-" + id + "@shopflash.local";
    }

    private static String fixtureId() {
        return "cart" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

}
