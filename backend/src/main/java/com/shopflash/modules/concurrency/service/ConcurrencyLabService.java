package com.shopflash.modules.concurrency.service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.dto.request.LabRunRequest;
import com.shopflash.modules.concurrency.dto.response.LabRunResponse;
import com.shopflash.modules.concurrency.dto.response.LabRunResponse.Config;
import com.shopflash.modules.concurrency.dto.response.LabRunResponse.StrategyResult;
import com.shopflash.modules.concurrency.dto.response.LabStateResponse;
import com.shopflash.modules.concurrency.lab.AttemptOutcome;
import com.shopflash.modules.concurrency.lab.LabRunner;
import com.shopflash.modules.concurrency.lab.LabRunner.RawRun;
import com.shopflash.modules.concurrency.lab.LabStats;
import com.shopflash.modules.concurrency.lab.LabStats.RunMetrics;
import com.shopflash.modules.concurrency.lab.LabStrategy;
import com.shopflash.modules.concurrency.repository.oracle.LabOracleCounter;
import com.shopflash.modules.concurrency.repository.redis.LabRedisCounter;
import com.zaxxer.hikari.HikariDataSource;

import redis.clients.jedis.RedisClient;

/**
 * Concurrency Lab: chạy kịch bản "N người tranh M suất" cho từng chiến lược, mỗi lần chạy đặt lại bộ đếm trước.
 * Chỉ một lần chạy tại một thời điểm trong một backend (các lần chạy tranh cùng một bộ đếm và cùng pool kết nối,
 * chạy chồng thì số đo vô nghĩa); lần thứ hai nhận 409 LAB_RUN_IN_PROGRESS.
 */
@Service
public class ConcurrencyLabService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLabService.class);

    static final int DEFAULT_BUYERS = 1000;
    static final int DEFAULT_QUOTA = 100;
    static final int DEFAULT_CONCURRENCY = 64;
    static final int DEFAULT_RUNS = 5;
    static final int DEFAULT_WARMUP = 1;

    private final LabOracleCounter oracle;
    private final LabRedisCounter redis;
    private final DataSource dataSource;
    private final RedisClient redisClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ConcurrencyLabService(LabOracleCounter oracle, LabRedisCounter redis, DataSource dataSource,
            RedisClient redisClient) {
        this.oracle = oracle;
        this.redis = redis;
        this.dataSource = dataSource;
        this.redisClient = redisClient;
    }

    public LabRunResponse run(LabRunRequest request) {
        LabRunRequest r = request == null ? new LabRunRequest(null, null, null, null, null, null) : request;
        int buyers = orDefault(r.buyers(), DEFAULT_BUYERS);
        int quota = orDefault(r.quota(), DEFAULT_QUOTA);
        int concurrency = orDefault(r.concurrency(), DEFAULT_CONCURRENCY);
        int runs = orDefault(r.runs(), DEFAULT_RUNS);
        int warmup = orDefault(r.warmup(), DEFAULT_WARMUP);
        if (quota > buyers) {
            throw ApiException.badRequest("quota", "quota phải nhỏ hơn hoặc bằng buyers");
        }
        Set<LabStrategy> strategies = r.strategies() == null || r.strategies().isEmpty()
                ? EnumSet.allOf(LabStrategy.class)
                : new LinkedHashSet<>(r.strategies());
        if (strategies.contains(null)) {
            throw ApiException.badRequest("strategies", "Có chiến lược rỗng");
        }

        acquire();
        try {
            Config config = new Config(buyers, quota, concurrency, runs, warmup, oraclePoolSize(), redisPoolSize(),
                    LabStrategy.MAX_RETRIES);
            List<StrategyResult> results = new ArrayList<>();
            for (LabStrategy strategy : strategies) {
                results.add(runStrategy(strategy, config));
            }
            return new LabRunResponse(config, results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "LAB_INTERRUPTED",
                    "Lần chạy Lab bị dừng giữa chừng", null, e);
        } finally {
            running.set(false);
        }
    }

    public LabStateResponse reset(int quota) {
        acquire();
        try {
            oracle.reset(quota);
            redis.reset(quota);
        } finally {
            running.set(false);
        }
        return state();
    }

    public LabStateResponse state() {
        LabStateResponse.Oracle o = oracle.state()
                .map(row -> new LabStateResponse.Oracle(row.allocated(), row.sold(), row.version()))
                .orElse(null);
        LabStateResponse.Redis rd = redis.state()
                .map(s -> new LabStateResponse.Redis(s.allocated(), s.remaining(), s.version()))
                .orElse(null);
        return new LabStateResponse(o, rd, running.get());
    }

    private StrategyResult runStrategy(LabStrategy strategy, Config config) throws InterruptedException {
        Supplier<AttemptOutcome> attempt = strategy.store() == LabStrategy.Store.ORACLE
                ? () -> oracle.buy(strategy)
                : () -> redis.buy(strategy);
        for (int i = 0; i < config.warmup(); i++) {
            runOnce(strategy, attempt, config);
        }
        List<RunMetrics> measured = new ArrayList<>(config.runs());
        for (int i = 1; i <= config.runs(); i++) {
            RawRun raw = runOnce(strategy, attempt, config);
            RunMetrics metrics = LabStats.measure(i, config.quota(), raw, finalSold(strategy.store()));
            measured.add(metrics);
            log.info("Lab {} lần {}: successes={} oversold={} lostUpdates={} errors={} elapsedMs={}", strategy, i,
                    metrics.successes(), metrics.oversold(), metrics.lostUpdates(), metrics.errorCodes(),
                    metrics.elapsedMs());
        }
        return new StrategyResult(strategy, strategy.store(), LabStats.summarize(measured), measured);
    }

    private RawRun runOnce(LabStrategy strategy, Supplier<AttemptOutcome> attempt, Config config)
            throws InterruptedException {
        if (strategy.store() == LabStrategy.Store.ORACLE) {
            oracle.reset(config.quota());
        } else {
            redis.reset(config.quota());
        }
        return LabRunner.run(config.buyers(), config.concurrency(), attempt);
    }

    private long finalSold(LabStrategy.Store store) {
        if (store == LabStrategy.Store.ORACLE) {
            return oracle.state().orElseThrow().sold();
        }
        LabRedisCounter.State s = redis.state().orElseThrow();
        return s.allocated() - s.remaining();
    }

    private void acquire() {
        if (!running.compareAndSet(false, true)) {
            throw ApiException.conflict("LAB_RUN_IN_PROGRESS", "Lab đang chạy, đợi lần chạy hiện tại xong");
        }
    }

    private int oraclePoolSize() {
        try {
            return dataSource.isWrapperFor(HikariDataSource.class)
                    ? dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize()
                    : -1;
        } catch (SQLException e) {
            return -1;
        }
    }

    private int redisPoolSize() {
        return redisClient.getPool() == null ? -1 : redisClient.getPool().getMaxTotal();
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
