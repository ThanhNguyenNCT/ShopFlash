package com.shopflash.modules.transaction.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisProtocol;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/** Mỗi test chỉ dùng txlab:<UUID>:*; hậu kiểm bằng RedisClient khác kết nối giao dịch. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
class RedisTransactionIT {
    @Autowired RedisClient reader;
    @Value("${spring.data.redis.host:localhost}") String host;
    @Value("${spring.data.redis.port:6379}") int port;
    @Value("${spring.data.redis.password:}") String password;
    @Value("${spring.data.redis.database:0}") int database;

    private String prefix;

    @BeforeEach
    void setup() {
        prefix = "txlab:redis" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ":";
    }

    @AfterEach
    void cleanup() {
        ScanParams params = new ScanParams().match(prefix + "*").count(100);
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> page = reader.scan(cursor, params);
            if (!page.getResult().isEmpty()) reader.unlink(page.getResult().toArray(String[]::new));
            cursor = page.getCursor();
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
    }

    @Test
    void multiExecAppliesAllValidCommands() {
        String cart = key("cart");
        String stock = key("stock");
        String order = key("order");
        reader.set(cart, "1");
        reader.set(stock, "100");
        String before = state(cart, stock, order);
        List<Object> replies;
        try (Jedis writer = session()) {
            Transaction tx = writer.multi();
            tx.set(order, "1");
            tx.decr(stock);
            tx.del(cart);
            replies = tx.exec();
        }
        String actual = state(cart, stock, order);
        String expected = "cart=null,stock=99,order=1";
        evidence("redis_multi_exec", "MULTI; SET order; DECR stock; DEL cart; EXEC (" + replies.size() + " replies)",
                before, expected, actual, "GET cart/stock/order bằng RedisClient riêng");
        assertThat(replies).hasSize(3);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void discardDropsOnlyQueuedCommands() {
        String cart = key("cart");
        String stock = key("stock");
        String order = key("order");
        reader.set(cart, "1");
        reader.set(stock, "100");
        String before = state(cart, stock, order);
        try (Jedis writer = session()) {
            Transaction tx = writer.multi();
            tx.set(order, "1");
            tx.decr(stock);
            tx.del(cart);
            assertThat(tx.discard()).isEqualTo("OK");
        }
        String actual = state(cart, stock, order);
        evidence("redis_discard", "MULTI; SET/DECR/DEL xếp hàng; DISCARD trước EXEC",
                before, before, actual, "GET cart/stock/order; không có lệnh xếp hàng đã chạy");
        assertThat(actual).isEqualTo(before);
    }

    @Test
    void runtimeErrorAtExecDoesNotRollbackOtherCommands() {
        String cart = key("cart");
        String stock = key("stock");
        String order = key("order");
        reader.set(cart, "1");
        reader.set(stock, "not-an-integer");
        String before = state(cart, stock, order);
        List<Object> replies;
        try (Jedis writer = session()) {
            Transaction tx = writer.multi();
            tx.set(order, "1");
            tx.decr(stock);
            tx.del(cart);
            replies = tx.exec();
        }
        String actual = state(cart, stock, order);
        String expected = "cart=null,stock=not-an-integer,order=1";
        boolean hasError = replies.stream().anyMatch(JedisDataException.class::isInstance);
        SemanticsEvidence.record("redis", "redis_exec_runtime_error", "MULTI; SET order; DECR stock sai kiểu; DEL cart; EXEC",
                before, expected, actual, "EXEC reply lỗi một lệnh; GET bằng RedisClient riêng",
                hasError && expected.equals(actual));
        assertThat(hasError).isTrue();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void watchAbortsQueuedWritesAfterCompetitorChangesKey() throws Exception {
        String cart = key("cart");
        String order = key("order");
        reader.set(cart, "1");
        String before = state(cart, order);
        CountDownLatch watched = new CountDownLatch(1);
        try (Jedis watcher = session(); var workers = Executors.newSingleThreadExecutor()) {
            var competitor = workers.submit(() -> {
                try {
                    assertThat(watched.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
                try (Jedis other = session()) { other.set(cart, "2"); }
            });
            assertThat(watcher.watch(cart)).isEqualTo("OK");
            watched.countDown();
            competitor.get(8, TimeUnit.SECONDS);
            Transaction tx = watcher.multi();
            tx.set(order, "1");
            tx.del(cart);
            assertThat(tx.exec()).isNull();
        }
        String actual = state(cart, order);
        String expected = "cart=2,order=null";
        evidence("redis_watch_conflict", "A WATCH cart; latch; B SET cart=2; A MULTI SET/DEL; EXEC trả nil",
                before, expected, actual, "CountDownLatch sau WATCH; GET bằng RedisClient riêng");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void luaRuntimeErrorKeepsEarlierWrite() {
        String order = key("order");
        String stock = key("stock");
        reader.set(stock, "not-an-integer");
        String before = state(order, stock);
        String script = "redis.call('SET', KEYS[1], 'written'); return redis.call('DECR', KEYS[2])";
        assertThatThrownBy(() -> {
            try (Jedis writer = session()) {
                writer.eval(script, List.of(order, stock), List.of());
            }
        }).isInstanceOf(JedisDataException.class);
        String actual = state(order, stock);
        String expected = "order=written,stock=not-an-integer";
        evidence("redis_lua_partial_write", "EVAL: SET order; DECR stock sai kiểu; script lỗi",
                before, expected, actual, "GET bằng RedisClient riêng sau lỗi Lua");
        assertThat(actual).isEqualTo(expected);
    }

    private Jedis session() {
        DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
                .database(database).timeoutMillis(10000).protocol(RedisProtocol.RESP2);
        if (password != null && !password.isBlank()) config.password(password);
        return new Jedis(host, port, config.build());
    }

    private String key(String suffix) { return prefix + suffix; }

    private String state(String... keys) {
        return java.util.Arrays.stream(keys)
                .map(key -> key.substring(prefix.length()) + "=" + reader.get(key))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static void evidence(String scenario, String steps, String before, String expected,
                                 String actual, String verification) {
        SemanticsEvidence.record("redis", scenario, steps, before, expected, actual, verification, expected.equals(actual));
    }
}
