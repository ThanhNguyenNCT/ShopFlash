package com.shopflash.modules.transaction.study;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import com.shopflash.modules.transaction.fixture.TransactionFixtures;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.Transaction;

/** Chỉ gọi đích danh bằng -Dtest=TransactionDbBenchmarkIT. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
class TransactionDbBenchmarkIT {
    private static final String SECRET = Base64.getEncoder()
            .encodeToString("tx-benchmark-test-secret-32-bytes!".getBytes(StandardCharsets.UTF_8));
    private static final String[] CSV_HEADER = {
        "run_id", "scenario", "dbms_or_http", "api_or_flow", "cart_size", "concurrency",
        "round", "iteration", "started_at", "duration_ms", "outcome", "error_code", "fixture", "invariant_violations"
    };

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired DataSource dataSource;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;
    @Value("${spring.data.redis.host:localhost}") String redisHost;
    @Value("${spring.data.redis.port:6379}") int redisPort;
    @Value("${spring.data.redis.password:}") String redisPassword;
    @Value("${spring.data.redis.database:0}") int redisDatabase;

    record Fixture(String id, long userId, long cartId, long[] products) {
        String prefix() { return "txlab:" + id + ":bench:"; }
    }
    record Sample(String runId, String scenario, String dbms, int size, int concurrency, int round,
                  int iteration, String startedAt, double durationMs, String outcome, String errorCode,
                  String fixture, int violations) {}
    record Round(List<Sample> samples, long wallNs) {}

    @Test
    void benchmarkEquivalentCheckoutWorkload() throws Exception {
        int warmup = positive("tx.bench.warmup", 50);
        int rounds = positive("tx.bench.rounds", 5);
        int samples = positive("tx.bench.samples", 200);
        if ((warmup != 50 || rounds != 5 || samples != 200)
                && System.getProperty("tx.bench.override.reason", "").isBlank()) {
            throw new IllegalArgumentException("Cần -Dtx.bench.override.reason khi đổi số mẫu mặc định");
        }
        String runId = "db-" + Instant.now().toString().replace(":", "-").replace(".", "-");
        Path output = Path.of(System.getProperty("tx.bench.output.dir", "../report/transaction/results"));
        Files.createDirectories(output);
        Path rawCsv = output.resolve(runId + ".csv");
        Path rawJson = output.resolve(runId + ".jsonl");
        Path runsJson = output.resolve(runId + "-runs.jsonl");
        Path configJson = output.resolve(runId + "-config.json");
        Files.writeString(rawCsv, String.join(",", CSV_HEADER) + "\n", StandardCharsets.UTF_8);
        Files.writeString(rawJson, "", StandardCharsets.UTF_8);
        Files.writeString(runsJson, "", StandardCharsets.UTF_8);
        Files.writeString(configJson, "{" +
                "\"run_id\":" + json(runId) + ",\"warmup\":" + warmup + ",\"rounds\":" + rounds +
                ",\"samples_per_round\":" + samples + ",\"cart_sizes\":[1,5],\"concurrency\":[1,10]," +
                "\"oracle_pool_max\":10,\"redis_connection\":\"one pinned Jedis per worker\"," +
                "\"redis_database\":" + redisDatabase + ",\"fixture_stock\":100,\"unit_price\":100," +
                "\"quantity_per_line\":1,\"reset\":\"all fixture state before warm-up and each measured round\"," +
                "\"timing\":\"System.nanoTime from business read/lock to COMMIT or EXEC reply; excludes fixture reset and verification\"," +
                "\"throughput_wall\":\"from release of workers until all results and invariant checks finish\"," +
                "\"override_reason\":" + json(System.getProperty("tx.bench.override.reason", "")) + "}\n",
                StandardCharsets.UTF_8);

        TransactionFixtures fixtures = new TransactionFixtures(jdbc, manager, redis);
        int poolSize = Math.max(warmup, samples);
        List<Fixture> pool = new ArrayList<>(poolSize);
        try {
            for (int index = 0; index < poolSize; index++) {
                String id = "bm" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                fixtures.setup(id);
                pool.add(resolveFixture(id));
            }
            for (String dbms : List.of("oracle", "redis")) {
                for (int size : List.of(1, 5)) {
                    for (int concurrency : List.of(1, 10)) {
                        prepare(fixtures, pool, dbms, size, warmup);
                        executeRound(runId, dbms, size, concurrency, 0, pool.subList(0, warmup));
                        for (int round = 1; round <= rounds; round++) {
                            prepare(fixtures, pool, dbms, size, samples);
                            Round measured = executeRound(runId, dbms, size, concurrency, round,
                                    pool.subList(0, samples));
                            append(rawCsv, rawJson, measured.samples());
                            long success = measured.samples().stream().filter(s -> s.outcome().equals("success")).count();
                            long violations = measured.samples().stream().mapToLong(Sample::violations).sum();
                            Files.writeString(runsJson, "{\"run_id\":" + json(runId) + ",\"scenario\":" +
                                    json(dbms + "-cart" + size + "-c" + concurrency) + ",\"dbms_or_http\":" + json(dbms) +
                                    ",\"cart_size\":" + size + ",\"concurrency\":" + concurrency +
                                    ",\"round\":" + round + ",\"completed\":" + measured.samples().size() +
                                    ",\"success\":" + success + ",\"invariant_violations\":" + violations +
                                    ",\"wall_seconds\":" + format(measured.wallNs() / 1_000_000_000.0) + "}\n",
                                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                            assertThat(violations).as("Bất biến sau round %s/%s", dbms, round).isZero();
                        }
                    }
                }
            }
        } finally {
            for (Fixture fixture : pool) fixtures.cleanup(fixture.id());
        }
    }

    private Fixture resolveFixture(String id) {
        long userId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = :email",
                Map.of("email", "txlab-" + id + "@shopflash.local"), Long.class);
        long cartId = jdbc.queryForObject("SELECT cart_id FROM carts WHERE user_id = :userId",
                Map.of("userId", userId), Long.class);
        long[] products = new long[5];
        for (int i = 0; i < 5; i++) {
            products[i] = jdbc.queryForObject("SELECT product_id FROM products WHERE sku = :sku",
                    Map.of("sku", "TXLAB-" + id.toUpperCase(Locale.ROOT) + "-" + (i + 1)), Long.class);
        }
        return new Fixture(id, userId, cartId, products);
    }

    private void prepare(TransactionFixtures fixtures, List<Fixture> pool, String dbms, int size, int count) {
        for (int index = 0; index < count; index++) {
            Fixture fixture = pool.get(index);
            fixtures.reset(fixture.id());
            if (dbms.equals("oracle")) {
                for (int line = 0; line < size; line++) {
                    jdbc.update("""
                            INSERT INTO cart_items (cart_id, product_id, quantity, created_at, updated_at)
                            VALUES (:cart, :product, 1, SYSTIMESTAMP, SYSTIMESTAMP)
                            """, Map.of("cart", fixture.cartId(), "product", fixture.products()[line]));
                }
            } else {
                redis.set(fixture.prefix() + "cart", Integer.toString(size));
                for (int line = 0; line < size; line++) {
                    redis.set(fixture.prefix() + "stock:" + line, "100");
                    redis.set(fixture.prefix() + "price:" + line, "100");
                }
            }
        }
    }

    private Round executeRound(String runId, String dbms, int size, int concurrency, int round,
                               List<Fixture> fixtures) throws Exception {
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<Sample>>> futures = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(concurrency)) {
            for (int worker = 0; worker < concurrency; worker++) {
                final int workerIndex = worker;
                futures.add(workers.submit((Callable<List<Sample>>) () -> {
                    List<Sample> own = new ArrayList<>();
                    try (Jedis jedis = dbms.equals("redis") ? redisSession() : null) {
                        ready.countDown();
                        if (!go.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Barrier benchmark timeout");
                        for (int iteration = workerIndex; iteration < fixtures.size(); iteration += concurrency) {
                            Fixture fixture = fixtures.get(iteration);
                            String startedAt = Instant.now().toString();
                            long start = System.nanoTime();
                            String outcome = "success";
                            String code = "";
                            try {
                                if (dbms.equals("oracle")) oracleCheckout(fixture, size);
                                else if (!redisCheckout(jedis, fixture, size)) {
                                    outcome = "rejected";
                                    code = "WATCH_ABORT";
                                }
                            } catch (SQLException error) {
                                outcome = error.getErrorCode() == 1 || error.getErrorCode() == 54 ? "rejected" : "error";
                                code = "ORA-" + error.getErrorCode();
                            } catch (Exception error) {
                                outcome = "error";
                                code = error.getClass().getSimpleName();
                            }
                            double durationMs = (System.nanoTime() - start) / 1_000_000.0;
                            int violations = verify(dbms, fixture, size, outcome);
                            if (violations > 0) outcome = "invalid";
                            if (!(durationMs > 0) || !Double.isFinite(durationMs)) outcome = "invalid";
                            own.add(new Sample(runId, dbms + "-cart" + size + "-c" + concurrency,
                                    dbms, size, concurrency, round, iteration + 1, startedAt,
                                    durationMs, outcome, code, fixture.id(), violations));
                        }
                    }
                    return own;
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            long wallStart = System.nanoTime();
            go.countDown();
            List<Sample> all = new ArrayList<>();
            for (Future<List<Sample>> future : futures) all.addAll(future.get(180, TimeUnit.SECONDS));
            long wallNs = System.nanoTime() - wallStart;
            return new Round(all, wallNs);
        }
    }

    private void oracleCheckout(Fixture fixture, int size) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                query(connection, "SELECT version FROM carts WHERE cart_id = ? FOR UPDATE", fixture.cartId());
                for (int line = 0; line < size; line++) {
                    long product = fixture.products()[line];
                    assertThat(query(connection, "SELECT quantity FROM cart_items WHERE cart_id = ? AND product_id = ?",
                            fixture.cartId(), product)).isEqualTo(1);
                    assertThat(query(connection, "SELECT base_price FROM products WHERE product_id = ? FOR UPDATE", product))
                            .isEqualTo(100);
                    assertThat(query(connection, "SELECT on_hand_quantity - reserved_flash_quantity FROM product_inventory WHERE product_id = ? FOR UPDATE",
                            product)).isGreaterThanOrEqualTo(1);
                }
                String code = "TXB-" + UUID.randomUUID().toString().replace("-", "");
                execute(connection, """
                        INSERT INTO orders (order_code, user_id, order_type, status, subtotal, discount_amount,
                          total_amount, recipient_name, recipient_phone, shipping_address, idempotency_key,
                          created_at, updated_at)
                        VALUES (?, ?, 'NORMAL', 'PENDING', ?, 0, ?, 'Benchmark', '0900000000', 'Fixture only', ?,
                          SYSTIMESTAMP, SYSTIMESTAMP)
                        """, code, fixture.userId(), BigDecimal.valueOf(size * 100L),
                        BigDecimal.valueOf(size * 100L), UUID.randomUUID().toString());
                long orderId = queryText(connection, "SELECT order_id FROM orders WHERE order_code = ?", code);
                for (int line = 0; line < size; line++) {
                    long product = fixture.products()[line];
                    execute(connection, """
                            INSERT INTO order_items (order_id, product_id, product_name, product_sku,
                              unit_price, quantity, line_total)
                            VALUES (?, ?, 'Fixture Product', ?, 100, 1, 100)
                            """, orderId, product, "TXLAB-" + (line + 1));
                    execute(connection, """
                            UPDATE product_inventory SET on_hand_quantity = on_hand_quantity - 1,
                              version = version + 1 WHERE product_id = ? AND on_hand_quantity - reserved_flash_quantity >= 1
                            """, product);
                }
                execute(connection, "DELETE FROM cart_items WHERE cart_id = ?", fixture.cartId());
                execute(connection, "UPDATE carts SET version = version + 1 WHERE cart_id = ?", fixture.cartId());
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    private boolean redisCheckout(Jedis jedis, Fixture fixture, int size) {
        String prefix = fixture.prefix();
        List<String> watched = new ArrayList<>();
        watched.add(prefix + "cart");
        for (int line = 0; line < size; line++) watched.add(prefix + "stock:" + line);
        jedis.watch(watched.toArray(String[]::new));
        if (!Integer.toString(size).equals(jedis.get(prefix + "cart"))) {
            jedis.unwatch();
            return false;
        }
        for (int line = 0; line < size; line++) {
            if (!"100".equals(jedis.get(prefix + "price:" + line))
                    || Integer.parseInt(jedis.get(prefix + "stock:" + line)) < 1) {
                jedis.unwatch();
                return false;
            }
        }
        Transaction tx = jedis.multi();
        tx.set(prefix + "order", Integer.toString(size * 100));
        for (int line = 0; line < size; line++) {
            tx.set(prefix + "line:" + line, "1:100");
            tx.decr(prefix + "stock:" + line);
        }
        tx.del(prefix + "cart");
        return tx.exec() != null;
    }

    private int verify(String dbms, Fixture fixture, int size, String outcome) {
        if (!outcome.equals("success")) return 0;
        try {
            if (dbms.equals("oracle")) {
                try (Connection check = dataSource.getConnection()) {
                    int violations = 0;
                    if (query(check, "SELECT COUNT(*) FROM orders WHERE user_id = ?", fixture.userId()) != 1) violations++;
                    if (query(check, "SELECT COUNT(*) FROM order_items WHERE order_id IN (SELECT order_id FROM orders WHERE user_id = ?)",
                            fixture.userId()) != size) violations++;
                    if (query(check, "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?", fixture.cartId()) != 0) violations++;
                    for (int line = 0; line < size; line++) {
                        if (query(check, "SELECT on_hand_quantity FROM product_inventory WHERE product_id = ?",
                                fixture.products()[line]) != 99) violations++;
                    }
                    return violations;
                }
            }
            int violations = redis.get(fixture.prefix() + "order").equals(Integer.toString(size * 100)) ? 0 : 1;
            if (redis.get(fixture.prefix() + "cart") != null) violations++;
            for (int line = 0; line < size; line++) {
                if (!"1:100".equals(redis.get(fixture.prefix() + "line:" + line))) violations++;
                if (!"99".equals(redis.get(fixture.prefix() + "stock:" + line))) violations++;
            }
            return violations;
        } catch (Exception error) {
            return 1;
        }
    }

    private Jedis redisSession() {
        DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
                .database(redisDatabase).timeoutMillis(30000).protocol(RedisProtocol.RESP2);
        if (redisPassword != null && !redisPassword.isBlank()) config.password(redisPassword);
        return new Jedis(redisHost, redisPort, config.build());
    }

    private static long query(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Missing benchmark row");
                return rows.getLong(1);
            }
        }
    }

    private static long queryText(Connection connection, String sql, String value) throws SQLException {
        return query(connection, sql, value);
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            if (statement.executeUpdate() < 1) throw new SQLException("Benchmark write rejected");
        }
    }

    private static int positive(String property, int fallback) {
        int value = Integer.getInteger(property, fallback);
        if (value < 1) throw new IllegalArgumentException(property + " phải dương");
        return value;
    }

    private static void append(Path csv, Path jsonl, List<Sample> samples) throws Exception {
        StringBuilder csvLines = new StringBuilder();
        StringBuilder jsonLines = new StringBuilder();
        for (Sample sample : samples) {
            csvLines.append(String.join(",", json(sample.runId()), json(sample.scenario()), json(sample.dbms()),
                    json("checkout"), Integer.toString(sample.size()), Integer.toString(sample.concurrency()),
                    Integer.toString(sample.round()), Integer.toString(sample.iteration()), json(sample.startedAt()),
                    format(sample.durationMs()), json(sample.outcome()), json(sample.errorCode()),
                    json(sample.fixture()), Integer.toString(sample.violations()))).append('\n');
            jsonLines.append("{\"run_id\":").append(json(sample.runId()))
                    .append(",\"scenario\":").append(json(sample.scenario()))
                    .append(",\"dbms_or_http\":").append(json(sample.dbms()))
                    .append(",\"api_or_flow\":\"checkout\",\"cart_size\":").append(sample.size())
                    .append(",\"concurrency\":").append(sample.concurrency())
                    .append(",\"round\":").append(sample.round())
                    .append(",\"iteration\":").append(sample.iteration())
                    .append(",\"started_at\":").append(json(sample.startedAt()))
                    .append(",\"duration_ms\":").append(format(sample.durationMs()))
                    .append(",\"outcome\":").append(json(sample.outcome()))
                    .append(",\"error_code\":").append(json(sample.errorCode()))
                    .append(",\"fixture\":").append(json(sample.fixture()))
                    .append(",\"invariant_violations\":").append(sample.violations()).append("}\n");
        }
        Files.writeString(csv, csvLines, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        Files.writeString(jsonl, jsonLines, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.6f", value); }
    private static String json(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
