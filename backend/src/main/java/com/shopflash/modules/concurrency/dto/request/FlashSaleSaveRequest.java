package com.shopflash.modules.concurrency.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Tạo (POST, bỏ version) hoặc sửa (PUT, bắt buộc version) chương trình. Giờ là ISO-8601 có offset.
 * DRAFT sửa được hết; SCHEDULED/ACTIVE chỉ sửa được name, description (gửi lại nguyên giờ và config cũ).
 * allowCancel = true chưa hỗ trợ (đơn flash sale chưa hủy được).
 */
public record FlashSaleSaveRequest(
        Long version,
        String name,
        String description,
        @NotNull OffsetDateTime startAt,
        @NotNull OffsetDateTime endAt,
        @Valid Config config) {

    public record Config(
            @Min(1) @Max(10) Integer maxQuantityPerUser,
            @Min(30) @Max(1800) Integer reservationTimeoutSeconds,
            Boolean allowCancel) {
    }
}
