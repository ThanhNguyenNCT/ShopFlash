package com.shopflash.modules.concurrency.repository.redis;

import java.io.IOException;

import org.springframework.http.HttpStatus;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.exception.FlashErrors;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

/** Đổi lỗi Jedis thành ApiException; thông báo gốc của Redis chỉ vào log, không trả ra client. */
public final class RedisFailures {

    private RedisFailures() {
    }

    public static RuntimeException translate(RuntimeException e) {
        if (e instanceof ApiException) {
            return e;
        }
        if (e instanceof JedisConnectionException || (e instanceof JedisException && e.getCause() instanceof IOException)) {
            return FlashErrors.redisUnavailable(e);
        }
        if (e instanceof JedisException) {
            return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_ERROR",
                    "Lỗi khi chạy lệnh Redis, xem log backend", null, e);
        }
        return e;
    }
}
