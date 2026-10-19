package com.shopflash.modules.backup.dto.response;

/** Artifact của một nguồn trong backup; filename luôn là đường dẫn tương đối trong backup id. */
public record BackupArtifactResponse(String source, String filename, long bytes, String sha256) {
}
