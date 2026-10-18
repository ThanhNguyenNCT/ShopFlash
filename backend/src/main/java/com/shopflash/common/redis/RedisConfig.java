package com.shopflash.common.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.RedisProtocol;

/**
 * Client Jedis dùng chung cho mọi module, dựng từ spring.data.redis.*. Inject theo kiểu: {@code RedisClient redis}.
 * Dùng RedisClient (bản thay JedisPooled đã deprecated trong Jedis 7), có pool kết nối. Ép RESP2 để reply của
 * FT.AGGREGATE/FT.PROFILE/FT.CURSOR là mảng phẳng (module query tự đọc reply thô, không phụ thuộc cách Jedis map RESP3).
 * Lưu ý: lệnh FT.* phải kèm TIMEOUT 0, vì Redis Stack mặc định TIMEOUT 500 + ON_TIMEOUT return (trả kết quả dở).
 */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "close")
    public RedisClient redisClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${shopflash.redis.timeout-ms:30000}") int timeoutMs) {
        DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
                .database(database)
                .timeoutMillis(timeoutMs)
                .protocol(RedisProtocol.RESP2)
                .clientName("shopflash-backend");
        if (password != null && !password.isBlank()) {
            config.password(password);
        }
        ConnectionPoolConfig pool = new ConnectionPoolConfig();
        pool.setMaxTotal(16);
        pool.setMaxIdle(8);
        pool.setMinIdle(1);
        return RedisClient.builder()
                .hostAndPort(host, port)
                .clientConfig(config.build())
                .poolConfig(pool)
                .build();
    }
}
