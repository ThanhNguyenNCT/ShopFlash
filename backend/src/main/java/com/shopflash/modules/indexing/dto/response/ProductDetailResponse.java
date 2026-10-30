package com.shopflash.modules.indexing.dto.response;

import java.math.BigDecimal;

/** Chi tiết sản phẩm, luôn đọc từ Oracle (dữ liệu gốc, tồn kho mới nhất). */
public record ProductDetailResponse(
        long productId,
        String sku,
        String name,
        String description,
        long categoryId,
        String categoryName,
        BigDecimal price,
        String thumbnailUrl,
        String status,
        long availableQuantity,
        boolean inStock) {
}
