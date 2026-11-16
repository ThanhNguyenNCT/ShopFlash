package com.shopflash.modules.concurrency.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Bỏ trống quantity (hoặc không gửi body) thì giữ 1 suất. Giới hạn mỗi người do reserve.lua kiểm tra. */
public record ReserveRequest(@Min(1) @Max(99) Integer quantity) {
}
