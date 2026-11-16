package com.shopflash.modules.query.repository.cache;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

/** Cache kết quả báo cáo dạng chuỗi JSON: GET, và SET ... PX (ghi và đặt hạn trong một lệnh). */
@Repository
public class RedisReportCacheStore implements ReportCacheStore {

    private final RedisClient redis;

    public RedisReportCacheStore(RedisClient redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.get(key));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        redis.set(key, value, SetParams.setParams().px(ttl.toMillis()));
    }
}
