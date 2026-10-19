package com.shopflash.modules.backup.service;

import com.shopflash.modules.backup.dto.response.BackupSystemStatusResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.commands.ProtocolCommand;

/** Đọc baseline Oracle/Redis cho F11. Backup runner sẽ được bổ sung ở giai đoạn sau. */
@Service
public class BackupStatusService {

    private static final ZoneOffset ZONE = ZoneOffset.ofHours(7);
    private static final int SCAN_COUNT = 1_000;
    private final NamedParameterJdbcTemplate jdbc;
    private final RedisClient redis;
    private final BackupRunner runner;

    public BackupStatusService(NamedParameterJdbcTemplate jdbc, RedisClient redis, BackupRunner runner) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.runner = runner;
    }

    @Transactional(readOnly = true)
    public BackupSystemStatusResponse status() {
        return new BackupSystemStatusResponse(
                oracleStatus(),
                redisStatus(),
                runnerStatus(),
                OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS)
        );
    }

    private BackupSystemStatusResponse.OracleStatus oracleStatus() {
        try {
            var result = jdbc.queryForObject("""
                    SELECT
                      (SELECT COUNT(*) FROM user_tables WHERE UPPER(table_name) <> 'FLYWAY_SCHEMA_HISTORY') AS application_tables,
                      (SELECT COUNT(*) FROM categories) AS categories,
                      (SELECT COUNT(*) FROM products) AS products,
                      (SELECT COUNT(*) FROM product_inventory) AS inventory_rows
                    FROM dual
                    """, Map.of(), (rs, rowNum) -> new BackupSystemStatusResponse.OracleStatus(
                    "UP", rs.getLong("application_tables"), rs.getLong("categories"), rs.getLong("products"), rs.getLong("inventory_rows")));
            return result == null ? oracleDown() : result;
        } catch (DataAccessException error) {
            return oracleDown();
        }
    }

    private BackupSystemStatusResponse.RedisStatus redisStatus() {
        try {
            redis.ping();
            return new BackupSystemStatusResponse.RedisStatus("UP", productDocumentCount(), longValue(call("DBSIZE")));
        } catch (RuntimeException error) {
            return new BackupSystemStatusResponse.RedisStatus("DOWN", null, null);
        }
    }

    private BackupSystemStatusResponse.RunnerStatus runnerStatus() {
        BackupRunner.Availability availability = runner.availability();
        return new BackupSystemStatusResponse.RunnerStatus(availability.configured(), availability.state(), availability.message());
    }

    private long productDocumentCount() {
        long count = 0;
        String cursor = "0";
        do {
            List<?> reply = list(call("SCAN", cursor, "MATCH", "product:*", "COUNT", String.valueOf(SCAN_COUNT)));
            cursor = text(reply.get(0));
            count += list(reply.get(1)).size();
        } while (!"0".equals(cursor));
        return count;
    }

    private Object call(String command, String... args) {
        return redis.sendCommand(protocol(command), args);
    }

    private static BackupSystemStatusResponse.OracleStatus oracleDown() {
        return new BackupSystemStatusResponse.OracleStatus("DOWN", null, null, null, null);
    }

    private static ProtocolCommand protocol(String command) {
        byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
        return () -> bytes;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private static String text(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(text(value));
    }
}
