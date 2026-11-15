package com.shopflash.modules.concurrency.repository.redis;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.lab.AttemptOutcome;
import com.shopflash.modules.concurrency.lab.LabStrategy;
import com.shopflash.modules.concurrency.repository.redis.LuaScripts.Script;

import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.RedisClient;

/**
 * Bộ đếm Redis của Concurrency Lab: HASH cclab:item (allocated, remaining, version). Mỗi phương thức mua là
 * một chiến lược, một lượt mua 1 suất. Lỗi Jedis để nguyên cho bộ chạy đếm theo mã.
 */
@Repository
public class LabRedisCounter {

    public static final String KEY = "cclab:item";

    public record State(long allocated, long remaining, long version) {
    }

    private final RedisClient redis;
    private final LuaScripts scripts;

    public LabRedisCounter(RedisClient redis, LuaScripts scripts) {
        this.redis = redis;
        this.scripts = scripts;
    }

    /** Tạo lại key với allocated = remaining = quota. */
    public void reset(int quota) {
        try {
            redis.del(KEY);
            redis.hset(KEY, Map.of("allocated", String.valueOf(quota), "remaining", String.valueOf(quota),
                    "version", "0"));
        } catch (RuntimeException e) {
            throw RedisFailures.translate(e);
        }
    }

    public Optional<State> state() {
        try {
            List<String> values = redis.hmget(KEY, "allocated", "remaining", "version");
            if (values.get(0) == null) {
                return Optional.empty();
            }
            return Optional.of(new State(Long.parseLong(values.get(0)), Long.parseLong(values.get(1)),
                    Long.parseLong(values.get(2))));
        } catch (RuntimeException e) {
            throw RedisFailures.translate(e);
        }
    }

    /** Một lượt mua theo chiến lược Redis. */
    public AttemptOutcome buy(LabStrategy strategy) {
        return switch (strategy) {
            case REDIS_NAIVE -> naive();
            case REDIS_WATCH -> watch();
            case REDIS_DECR -> decrThenCompensate();
            case REDIS_LUA -> lua();
            default -> throw new IllegalArgumentException("Không phải chiến lược Redis: " + strategy);
        };
    }

    /** HGET, kiểm tra ở Java, rồi HSET giá trị đã tính sẵn: hai lượt đọc cùng giá trị thì ghi đè nhau. */
    private AttemptOutcome naive() {
        long remaining = parseRemaining(redis.hget(KEY, "remaining"));
        if (remaining <= 0) {
            return AttemptOutcome.soldOut(0);
        }
        redis.hset(KEY, "remaining", String.valueOf(remaining - 1));
        return AttemptOutcome.success(0);
    }

    /**
     * WATCH, đọc, MULTI, trừ, EXEC. Key bị lượt khác sửa sau WATCH thì EXEC trả null, thử lại. Phải chạy trên
     * kết nối riêng của transaction (transaction(false) chưa gửi MULTI): lệnh đọc trước multi() chạy ngay.
     * Đóng transaction thì Jedis gửi UNWATCH/DISCARD nếu cần và trả kết nối về pool.
     */
    private AttemptOutcome watch() {
        for (int retries = 0; retries <= LabStrategy.MAX_RETRIES; retries++) {
            try (AbstractTransaction tx = redis.transaction(false)) {
                tx.watch(KEY);
                long remaining = parseRemaining(tx.hget(KEY, "remaining").get());
                if (remaining <= 0) {
                    return AttemptOutcome.soldOut(retries);
                }
                tx.multi();
                tx.hincrBy(KEY, "remaining", -1);
                tx.hincrBy(KEY, "version", 1);
                if (tx.exec() != null) {
                    return AttemptOutcome.success(retries);
                }
            }
        }
        return AttemptOutcome.error("RETRY_EXHAUSTED", LabStrategy.MAX_RETRIES);
    }

    /** Trừ trước bằng HINCRBY (nguyên tử), âm thì cộng lại. Đúng số bán, nhưng bộ đếm có lúc âm. */
    private AttemptOutcome decrThenCompensate() {
        long after = redis.hincrBy(KEY, "remaining", -1);
        if (after < 0) {
            redis.hincrBy(KEY, "remaining", 1);
            return AttemptOutcome.soldOut(0);
        }
        return AttemptOutcome.success(0);
    }

    /** Kiểm tra và trừ trong một Lua script. */
    private AttemptOutcome lua() {
        Object reply = scripts.run(Script.LAB_BUY, List.of(KEY), List.of());
        long code = (Long) reply;
        if (code == 1) {
            return AttemptOutcome.success(0);
        }
        if (code == 0) {
            return AttemptOutcome.soldOut(0);
        }
        throw labNotReset();
    }

    private static long parseRemaining(String value) {
        if (value == null) {
            throw labNotReset();
        }
        return Long.parseLong(value);
    }

    private static ApiException labNotReset() {
        return ApiException.conflict("LAB_NOT_RESET", "Bộ đếm Lab trên Redis chưa được tạo, gọi reset trước");
    }
}
