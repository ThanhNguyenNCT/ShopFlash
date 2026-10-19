package com.shopflash.modules.storage.dto.response;

import java.util.List;

/** Danh sách phân trang dùng chung trong module storage. */
public record PageResponse<T>(List<T> rows, int page, int size, long total) {
}
