package com.shopflash.modules.storage.model;

import java.time.OffsetDateTime;

/** Bản ghi domain ánh xạ bảng CATEGORIES. */
public record Category(
        long categoryId,
        String name,
        String slug,
        String description,
        CategoryStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
