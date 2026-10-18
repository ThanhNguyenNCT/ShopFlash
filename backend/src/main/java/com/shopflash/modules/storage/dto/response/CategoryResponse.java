package com.shopflash.modules.storage.dto.response;

import com.shopflash.modules.storage.model.CategoryStatus;
import java.time.OffsetDateTime;

public record CategoryResponse(long categoryId, String name, String slug, String description,
        CategoryStatus status, long productCount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
