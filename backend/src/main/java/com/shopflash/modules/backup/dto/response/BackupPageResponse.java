package com.shopflash.modules.backup.dto.response;

import java.util.List;

/** Danh sách manifest backup trên máy local, phân trang phía backend. */
public record BackupPageResponse(List<BackupRecordResponse> rows, int page, int size, long total) {
}
