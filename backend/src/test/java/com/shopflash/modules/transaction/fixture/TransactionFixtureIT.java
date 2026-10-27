package com.shopflash.modules.transaction.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import redis.clients.jedis.RedisClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
class TransactionFixtureIT {
    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> Base64.getEncoder()
                .encodeToString("tx-fixture-test-secret-32-bytes!!".getBytes()));
    }

    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;
    @Autowired DataSource dataSource;

    @Test
    void setupResetAndCleanupAreRepeatableAndScoped() throws Exception {
        TransactionFixtures fixtures = new TransactionFixtures(jdbc, manager, redis);
        String first = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String second = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            fixtures.setup(first);
            fixtures.setup(first);
            fixtures.setup(second);
            assertThat(fixtures.snapshot(first)).isEqualTo(new TransactionFixtures.Snapshot(1, 5, 1, 0, 0, true));
            assertThat(fixtures.snapshot(second)).isEqualTo(new TransactionFixtures.Snapshot(1, 5, 1, 0, 0, true));

            // Kết nối độc lập phải nhìn thấy dữ liệu đã commit, không dựa vào transaction của JUnit.
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?")) {
                statement.setString(1, "txlab-" + first + "@shopflash.local");
                try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(1);
                }
            }

            long cartId = jdbc.queryForObject("""
                    SELECT c.cart_id FROM carts c JOIN users u ON u.user_id = c.user_id
                    WHERE u.email = :email
                    """, Map.of("email", "txlab-" + first + "@shopflash.local"), Long.class);
            long productId = jdbc.queryForObject("""
                    SELECT MIN(p.product_id) FROM products p JOIN categories c ON c.category_id = p.category_id
                    WHERE c.slug = :slug
                    """, Map.of("slug", "txlab-" + first), Long.class);
            jdbc.update("""
                    INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                    VALUES (:cartId, :productId, 2, SYSTIMESTAMP, SYSTIMESTAMP)
                    """, Map.of("cartId", cartId, "productId", productId));
            redis.set("txlab:" + first + ":extra", "x");
            assertThat(fixtures.snapshot(first).cartItems()).isEqualTo(1);

            fixtures.reset(first);
            fixtures.reset(first);
            assertThat(fixtures.snapshot(first)).isEqualTo(new TransactionFixtures.Snapshot(1, 5, 1, 0, 0, true));
            assertThat(redis.exists("txlab:" + first + ":extra")).isFalse();
            fixtures.cleanup(first);
            fixtures.cleanup(first);
            assertThat(fixtures.snapshot(first)).isEqualTo(new TransactionFixtures.Snapshot(0, 0, 0, 0, 0, false));
            assertThat(fixtures.snapshot(second).users()).isEqualTo(1);
        } finally {
            fixtures.cleanup(first);
            fixtures.cleanup(second);
        }
    }
}
