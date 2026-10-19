package com.shopflash.modules.backup.dto.response;

import java.time.OffsetDateTime;

/** Cũng là nội dung manifest.json; không chứa đường dẫn tuyệt đối hoặc output lệnh hệ thống. */
public record BackupRecordResponse(
        String backupId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        BackupArtifactResponse oracle,
        BackupArtifactResponse redis,
        BackupSystemStatusResponse baseline,
        String failureCode
) {
}
