package com.shopflash.modules.indexing.dto.response;

import java.math.BigDecimal;

/** Một sản phẩm trong danh sách kết quả (thẻ ProductCard). */
public record ProductItem(
        long productId,
        String sku,
        String name,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        boolean inStock,
        String thumbnailUrl) {
}
