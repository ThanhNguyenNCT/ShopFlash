package com.shopflash.modules.storage.dto.response;

import java.util.List;

/** Kết quả demo có kiểm soát: RedisJSON nhận document sai quy tắc Oracle rồi key được xóa ngay. */
public record RedisConstraintDemoResponse(
        boolean redisAccepted,
        boolean cleanedUp,
        String redisCommand,
        String storedDocument,
        List<String> oracleViolations
) {
}
