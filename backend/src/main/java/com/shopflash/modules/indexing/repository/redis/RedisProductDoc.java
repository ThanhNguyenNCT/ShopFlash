package com.shopflash.modules.indexing.repository.redis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** Document product:{id} do module storage ghi (DATABASE_DESIGN §18). Chỉ đọc. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RedisProductDoc(
        Long id,
        String sku,
        String name,
        Long categoryId,
        String categoryName,
        BigDecimal price,
        String status,
        Boolean inStock,
        String thumbnailUrl) {
}
