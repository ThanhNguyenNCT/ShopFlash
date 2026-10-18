package com.shopflash.modules.query.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopflash.modules.query.dto.request.StatsRequest;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.repository.oracle.OracleReportSource;
import com.shopflash.modules.query.repository.redis.RedisCommandRunner;
import com.shopflash.modules.query.repository.redis.RedisReportSource;
import com.shopflash.modules.query.service.AnalyticsProjectionService;
import com.shopflash.modules.query.service.OracleStatsService;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Các chốt chặn đã thêm sau code review: rebuild đồng thời, dọn cursor, không lộ thông báo của driver. */
class QueryHardeningCoreTest {

    static final ReportQuery CLIENT_QUERY = new ReportQuery(Report.REVENUE_BY_MONTH, LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31), JoinHint.AUTO, RedisMode.CLIENT, 10, 1, 20);

    @Test
    void secondConcurrentRebuildIsRejectedWith409() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RedisCommandRunner redis = new RedisCommandRunner(null) {
            @Override
            public List<Object> pipeline(List<String[]> commands) {
                // Bước đầu của rebuild (FT.INFO); giữ lock ở đây cho đến khi test cho chạy tiếp.
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new ApiException(ErrorCode.REDIS_NOT_BUILT, "chưa có index");
            }
        };
        AnalyticsProjectionService service = new AnalyticsProjectionService(new DriverManagerDataSource(), redis);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(service::rebuild);
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(service::rebuild)
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.REBUILD_IN_PROGRESS);
                        assertThat(((ApiException) e).code().status().value()).isEqualTo(409);
                        assertThat(e.getMessage()).contains("Đang có một lần dựng lại");
                    });

            release.countDown();
            assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(ApiException.class);
            // Lock đã nhả: lần sau chạy lại được (và lỗi theo stub, không phải 409).
            assertThatThrownBy(service::rebuild)
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.REDIS_NOT_BUILT));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    /** RedisCommandRunner giả cho CLIENT: trang đầu trả 1 document và cursorId 42, FT.CURSOR READ lỗi. */
    static final class CursorRedis extends RedisCommandRunner {
        final List<List<String>> calls = new ArrayList<>();
        boolean failDelete;

        CursorRedis() {
            super(null);
        }

        @Override
        public Object call(String... args) {
            calls.add(List.of(args));
            if (args[0].equals("FT.AGGREGATE")) {
                List<Object> row = List.of(b("createdAt"), b("1767225600"), b("totalAmount"), b("100"));
                return List.of(List.of(1L, row), 42L);
            }
            if (args[0].equals("FT.CURSOR") && args[1].equals("READ")) {
                throw new ApiException(ErrorCode.REDIS_UNAVAILABLE, "Không kết nối được Redis");
            }
            if (args[0].equals("FT.CURSOR") && args[1].equals("DEL") && failDelete) {
                throw new IllegalStateException("cursor not found");
            }
            return "OK";
        }

        private static byte[] b(String s) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Test
    void cursorIsDeletedWhenClientReadFails() {
        CursorRedis redis = new CursorRedis();
        assertThatThrownBy(() -> new RedisReportSource(redis).run(CLIENT_QUERY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE));
        assertThat(redis.calls).last().isEqualTo(List.of("FT.CURSOR", "DEL", "idx:order-analytics", "42"));
    }

    @Test
    void cursorCleanupErrorDoesNotHideOriginalError() {
        CursorRedis redis = new CursorRedis();
        redis.failDelete = true;
        assertThatThrownBy(() -> new RedisReportSource(redis).run(CLIENT_QUERY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE));
        assertThat(redis.calls).hasSize(3);
    }

    @Test
    void cursorNotDeletedWhenFullyRead() {
        RedisCommandRunner redis = new RedisCommandRunner(null) {
            @Override
            public Object call(String... args) {
                if (args[0].equals("FT.CURSOR")) {
                    throw new AssertionError("không được gửi " + String.join(" ", args));
                }
                return List.of(List.of(0L), 0L);
            }
        };
        assertThat(new RedisReportSource(redis).run(CLIENT_QUERY).roundTrips()).isEqualTo(1);
    }

    /** DataSource luôn lỗi kết nối với thông báo kiểu driver Oracle (có host, user). */
    static DataSource failingOracle() {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        throw new SQLException(
                                "ORA-01017: invalid credential or not authorized; logon denied for SHOP@db-host:1521",
                                "72000", 1017);
                    }
                    if (method.getName().equals("toString")) {
                        return "failingOracle";
                    }
                    if (method.getName().equals("hashCode")) {
                        return 1;
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void oracleReportErrorHidesDriverTextButKeepsOraCode() {
        OracleReportSource source = new OracleReportSource(new NamedParameterJdbcTemplate(failingOracle()));
        ReportQuery q = CLIENT_QUERY.withRedisMode(RedisMode.AGGREGATE);
        assertThatThrownBy(() -> source.run(q))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.ORACLE_ERROR);
                    assertThat(e.getMessage()).isEqualTo("Oracle lỗi khi chạy báo cáo revenue-by-month");
                    assertThat(e.getMessage()).doesNotContain("ORA-").doesNotContain("SHOP@");
                    assertThat(e.details()).containsEntry("oraCode", "ORA-01017");
                    assertThat(e.getCause()).isNotNull();
                });
    }

    @Test
    void oracleStatsErrorHidesDriverTextButKeepsOraCode() {
        OracleStatsService stats = new OracleStatsService(new NamedParameterJdbcTemplate(failingOracle()));
        assertThatThrownBy(() -> stats.apply(StatsRequest.Action.GATHER))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.ORACLE_ERROR);
                    assertThat(e.getMessage()).isEqualTo("Oracle lỗi khi GATHER thống kê");
                    assertThat(e.details()).containsEntry("oraCode", "ORA-01017");
                });
    }

    @Test
    void oraCodeIsNullWithoutOraText() {
        assertThat(ApiException.oraCode(new RuntimeException("Connection refused"))).isNull();
        assertThat(ApiException.oracle("x", new RuntimeException("boom")).details()).isNull();
        assertThat(ApiException.oraCode(new RuntimeException("wrap", new SQLException("ORA-00942: table"))))
                .isEqualTo("ORA-00942");
    }

    @Test
    void redisErrorsDoNotEchoDriverText() {
        RuntimeException e = RedisCommandRunner.translate(
                new redis.clients.jedis.exceptions.JedisConnectionException("Failed to connect to redis-host:6379"));
        assertThat(e.getMessage()).isEqualTo("Không kết nối được Redis");
        RuntimeException missing = RedisCommandRunner.translate(
                new redis.clients.jedis.exceptions.JedisDataException("idx:order-analytics: no such index"));
        assertThat(((ApiException) missing).code()).isEqualTo(ErrorCode.REDIS_NOT_BUILT);
        assertThat(missing.getMessage()).doesNotContain("no such index");
    }
}
