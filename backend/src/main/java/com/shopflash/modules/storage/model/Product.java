package com.shopflash.modules.storage.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Bản ghi domain ánh xạ bảng PRODUCTS; tồn kho được giữ riêng trong ProductInventory. */
public record Product(
        long productId,
        long categoryId,
        String sku,
        String name,
        String slug,
        String description,
        BigDecimal basePrice,
        String thumbnailUrl,
        ProductStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
