package com.shopflash.modules.transaction.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import com.shopflash.modules.transaction.fixture.TransactionFixtures;

import redis.clients.jedis.RedisClient;

/** Thí nghiệm Oracle thật; không đặt @Transactional trên test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
class TransactionSemanticsIT {
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("tx-semantics-test-secret-32-bytes!".getBytes());

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired DataSource dataSource;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;

    private TransactionFixtures fixtures;
    private String fixtureId;
    private long userId;
    private long cartId;
    private long firstProduct;
    private long secondProduct;

    @BeforeEach
    void setup() {
        fixtureId = "sem" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        fixtures = new TransactionFixtures(jdbc, manager, redis);
        fixtures.setup(fixtureId);
        userId = id("SELECT user_id FROM users WHERE email = :value", "txlab-" + fixtureId + "@shopflash.local");
        cartId = jdbc.queryForObject("SELECT cart_id FROM carts WHERE user_id = :id", Map.of("id", userId), Long.class);
        firstProduct = id("SELECT product_id FROM products WHERE sku = :value", "TXLAB-" + fixtureId.toUpperCase() + "-1");
        secondProduct = id("SELECT product_id FROM products WHERE sku = :value", "TXLAB-" + fixtureId.toUpperCase() + "-2");
        jdbc.update("""
                INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                VALUES (:cart, :product, 1, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("cart", cartId, "product", firstProduct));
    }

    @AfterEach
    void cleanup() {
        fixtures.cleanup(fixtureId);
    }

    @Test
    void commitMakesAllOrderWritesVisibleTogether() throws Exception {
        String before = snapshot();
        try (Connection writer = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            writeOrder(writer);
            writer.commit();
        }
        String actual = snapshot();
        String expected = "orders=1,lines=1,cart=0,stock=99,version=1";
        evidence("oracle_commit", "INSERT order/item; UPDATE inventory; DELETE cart item; COMMIT",
                before, expected, actual, "SELECT COUNT(*) orders/order_items/cart_items; inventory; carts.version");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void rollbackAfterPartialWritesRestoresEveryTable() throws Exception {
        String before = snapshot();
        try (Connection writer = dataSource.getConnection()) {
            writer.setAutoCommit(false);
            try {
                writeOrder(writer);
                throw new IllegalStateException("failure injection after order, item and inventory writes");
            } catch (IllegalStateException expected) {
                writer.rollback();
            }
        }
        String actual = snapshot();
        evidence("oracle_rollback_partial", "INSERT order/item; UPDATE inventory; DELETE cart item; injected exception; ROLLBACK",
                before, before, actual, "SELECT COUNT(*) orders/order_items/cart_items; inventory; carts.version");
        assertThat(actual).isEqualTo(before);
    }

    @Test
    void readCommittedHidesUncommittedWriteThenSeesCommit() throws Exception {
        String before = snapshot();
        long beforeB;
        long afterB;
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection()) {
            a.setAutoCommit(false);
            b.setAutoCommit(false);
            b.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            update(a, "UPDATE product_inventory SET on_hand_quantity = 99 WHERE product_id = ?", firstProduct);
            beforeB = scalar(b, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", firstProduct);
            a.commit();
            afterB = scalar(b, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", firstProduct);
            b.commit();
        }
        String actual = "beforeCommitB=" + beforeB + ",afterCommitB=" + afterB + ",stock=" + stock(firstProduct);
        String expected = "beforeCommitB=100,afterCommitB=99,stock=99";
        evidence("oracle_read_committed", "A UPDATE chưa COMMIT; B SELECT; A COMMIT; B SELECT cùng session",
                before, expected, actual, "Hai Connection READ_COMMITTED; SELECT inventory trên kết nối B và hậu kiểm");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void cartAndInventoryLocksTimeoutWithoutLostUpdate() throws Exception {
        String before = snapshot();
        try (Connection holder = dataSource.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            scalar(holder, "SELECT version FROM carts WHERE cart_id = ? FOR UPDATE", cartId);
            CountDownLatch started = new CountDownLatch(1);
            Future<Integer> waiter = workers.submit(() -> {
                try (Connection blocked = dataSource.getConnection()) {
                    started.countDown();
                    scalar(blocked, "SELECT version FROM carts WHERE cart_id = ? FOR UPDATE WAIT 1", cartId);
                    return 0;
                } catch (SQLException error) {
                    return error.getErrorCode();
                }
            });
            assertThat(started.await(15, TimeUnit.SECONDS)).isTrue();
            int timeout = waiter.get(8, TimeUnit.SECONDS);
            assertThat(timeout).describedAs("Oracle cart lock timeout code").isIn(54, 30006);
            holder.commit();
            jdbc.update("UPDATE carts SET version = version + 1 WHERE cart_id = :id", Map.of("id", cartId));
        }
        String actual = "cartVersion=" + version() + ",stock=" + stock(firstProduct);
        evidence("oracle_cart_lock_timeout", "A SELECT CARTS FOR UPDATE; B UPDATE timeout; A COMMIT; retry B",
                before, "cartVersion=1,stock=100", actual, "SELECT CARTS FOR UPDATE WAIT 1; SELECT carts.version/inventory");
        assertThat(actual).isEqualTo("cartVersion=1,stock=100");

        try (Connection holder = dataSource.getConnection(); var workers = Executors.newSingleThreadExecutor()) {
            holder.setAutoCommit(false);
            scalar(holder, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ? FOR UPDATE", firstProduct);
            CountDownLatch started = new CountDownLatch(1);
            Future<Integer> waiter = workers.submit(() -> {
                try (Connection blocked = dataSource.getConnection()) {
                    started.countDown();
                    scalar(blocked, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ? FOR UPDATE WAIT 1", firstProduct);
                    return 0;
                } catch (SQLException error) {
                    return error.getErrorCode();
                }
            });
            assertThat(started.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(waiter.get(8, TimeUnit.SECONDS)).describedAs("Oracle inventory lock timeout code").isIn(54, 30006);
            holder.rollback();
        }
        String inventoryActual = "cartVersion=" + version() + ",stock=" + stock(firstProduct);
        evidence("oracle_inventory_lock_timeout", "A SELECT INVENTORY FOR UPDATE; B UPDATE timeout; A ROLLBACK",
                "cartVersion=1,stock=100", "cartVersion=1,stock=100", inventoryActual,
                "SELECT INVENTORY FOR UPDATE WAIT 1; SELECT carts.version/inventory");
        assertThat(inventoryActual).isEqualTo("cartVersion=1,stock=100");
    }

    @Test
    void controlledDeadlockRollsBackOneSession() throws Exception {
        String before = snapshot();
        CyclicBarrier bothLocked = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> a = workers.submit(() -> crossUpdate(firstProduct, secondProduct, bothLocked));
            Future<Integer> b = workers.submit(() -> crossUpdate(secondProduct, firstProduct, bothLocked));
            int resultA = a.get(20, TimeUnit.SECONDS);
            int resultB = b.get(20, TimeUnit.SECONDS);
            assertThat((resultA == 0 && resultB == 60) || (resultA == 60 && resultB == 0))
                    .describedAs("Oracle deadlock outcomes: %s / %s", resultA, resultB).isTrue();
        }
        String actual = "stock1=" + stock(firstProduct) + ",stock2=" + stock(secondProduct);
        boolean passed = actual.equals("stock1=99,stock2=100") || actual.equals("stock1=100,stock2=99");
        SemanticsEvidence.record("oracle", "oracle_deadlock", "A/B khóa hai inventory khác nhau; barrier; cập nhật chéo; ORA-00060; rollback bên lỗi",
                before, "một COMMIT, một ROLLBACK; tổng tồn=199", actual,
                "CyclicBarrier sau SELECT FOR UPDATE; SELECT inventory bằng kết nối mới", passed);
        assertThat(passed).isTrue();
    }

    private int crossUpdate(long own, long other, CyclicBarrier barrier) throws SQLException, BrokenBarrierException,
            InterruptedException, java.util.concurrent.TimeoutException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                scalar(connection, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ? FOR UPDATE", own);
                barrier.await(5, TimeUnit.SECONDS);
                update(connection, "UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - 1 WHERE product_id = ?", other);
                connection.commit();
                return 0;
            } catch (SQLException error) {
                connection.rollback();
                return error.getErrorCode();
            }
        }
    }

    private void writeOrder(Connection connection) throws SQLException {
        String code = "TX-" + UUID.randomUUID().toString().replace("-", "");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount,
                  total_amount, recipient_name, recipient_phone, shipping_address, idempotency_key,
                  created_at, updated_at)
                VALUES (?, ?, 'NORMAL', 'PENDING', 100, 0, 100, 'Fixture', '0900000000', 'Fixture address', ?,
                  SYSTIMESTAMP, SYSTIMESTAMP)
                """)) {
            statement.setString(1, code);
            statement.setLong(2, userId);
            statement.setString(3, UUID.randomUUID().toString());
            statement.executeUpdate();
        }
        long orderId;
        try (PreparedStatement statement = connection.prepareStatement("SELECT order_id FROM orders WHERE order_code = ?")) {
            statement.setString(1, code);
            try (ResultSet rows = statement.executeQuery()) { rows.next(); orderId = rows.getLong(1); }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                  unit_price, quantity, line_total)
                VALUES (?, ?, 'Fixture Product', 'TXLAB-1', 100, 1, 100)
                """)) {
            statement.setLong(1, orderId);
            statement.setLong(2, firstProduct);
            statement.executeUpdate();
        }
        update(connection, "UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - 1, version = version + 1 WHERE product_id = ?", firstProduct);
        update(connection, "DELETE FROM cart_items WHERE cart_id = ?", cartId);
        update(connection, "UPDATE carts SET version = version + 1 WHERE cart_id = ?", cartId);
    }

    private String snapshot() throws SQLException {
        try (Connection check = dataSource.getConnection()) {
            return "orders=" + scalar(check, "SELECT COUNT(*) FROM orders WHERE user_id = ?", userId)
                    + ",lines=" + scalar(check, "SELECT COUNT(*) FROM order_items WHERE order_id IN (SELECT order_id FROM orders WHERE user_id = ?)", userId)
                    + ",cart=" + scalar(check, "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", cartId)
                    + ",stock=" + scalar(check, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", firstProduct)
                    + ",version=" + scalar(check, "SELECT version FROM carts WHERE cart_id = ?", cartId);
        }
    }

    private long stock(long productId) throws SQLException {
        try (Connection check = dataSource.getConnection()) {
            return scalar(check, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?", productId);
        }
    }

    private long version() throws SQLException {
        try (Connection check = dataSource.getConnection()) {
            return scalar(check, "SELECT version FROM carts WHERE cart_id = ?", cartId);
        }
    }

    private long id(String sql, String value) {
        return jdbc.queryForObject(sql, Map.of("value", value), Long.class);
    }

    private static long scalar(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getLong(1);
            }
        }
    }

    private static void update(Connection connection, String sql, long... ids) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) statement.setLong(i + 1, ids[i]);
            statement.executeUpdate();
        }
    }

    private static void evidence(String scenario, String steps, String before, String expected,
                                 String actual, String query) {
        SemanticsEvidence.record("oracle", scenario, steps, before, expected, actual, query, expected.equals(actual));
    }
}
