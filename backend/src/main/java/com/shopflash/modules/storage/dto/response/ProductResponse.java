package com.shopflash.modules.storage.dto.response;

import com.shopflash.modules.storage.model.ProductStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(long productId, long categoryId, String categoryName, String sku, String name,
        String slug, String description, BigDecimal basePrice, String thumbnailUrl, ProductStatus status,
        boolean inStock, InventoryResponse inventory, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
