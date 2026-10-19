package com.shopflash.modules.backup.dto.response;

import java.time.OffsetDateTime;

/** Trạng thái đọc được cho trang F11; không tạo backup hoặc thay đổi dữ liệu. */
public record BackupSystemStatusResponse(
        OracleStatus oracle,
        RedisStatus redis,
        RunnerStatus runner,
        OffsetDateTime generatedAt
) {
    public record OracleStatus(String status, Long applicationTables, Long categories, Long products, Long inventoryRows) {
    }

    public record RedisStatus(String status, Long productDocuments, Long totalKeys) {
    }

    public record RunnerStatus(boolean configured, String state, String message) {
    }
}
