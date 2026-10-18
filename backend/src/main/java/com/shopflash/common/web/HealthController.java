package com.shopflash.common.web;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.RedisClient;

/**
 * GET /api/health: backend còn sống và kết nối được Oracle, Redis không. Dùng cho scripts/dev.* và để frontend báo
 * "backend chưa chạy" cho dễ hiểu. Trả 200 khi cả hai DB lên, 503 khi có DB xuống (body vẫn cho biết DB nào).
 */
@RestController
public class HealthController {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(7);

    private final JdbcTemplate jdbc;
    private final RedisClient redis;

    /** Spring dùng constructor này: JdbcTemplate riêng có timeout 3 s, không đổi JdbcTemplate dùng chung của app. */
    @Autowired
    public HealthController(DataSource dataSource, RedisClient redis) {
        this(withTimeout(dataSource), redis);
    }

    HealthController(JdbcTemplate jdbc, RedisClient redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    /** /api/health không được treo khi Oracle chậm. */
    private static JdbcTemplate withTimeout(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(3);
        return jdbc;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> oracle = check(() -> jdbc.queryForObject("SELECT 1 FROM dual", Integer.class));
        Map<String, Object> redisStatus = check(redis::ping);
        boolean up = "UP".equals(oracle.get("status")) && "UP".equals(redisStatus.get("status"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", up ? "UP" : "DEGRADED");
        body.put("oracle", oracle);
        body.put("redis", redisStatus);
        body.put("time", OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS));
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** Chạy một lệnh kiểm tra, đo thời gian; lỗi chỉ ghi tên loại lỗi, không lộ chi tiết kết nối. */
    private static Map<String, Object> check(Runnable probe) {
        long start = System.nanoTime();
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            probe.run();
            result.put("status", "UP");
        } catch (RuntimeException e) {
            result.put("status", "DOWN");
            result.put("error", e.getClass().getSimpleName());
        }
        result.put("latencyMs", Math.round((System.nanoTime() - start) / 10_000.0) / 100.0);
        return result;
    }
}
