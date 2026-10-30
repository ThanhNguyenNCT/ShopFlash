package com.shopflash.modules.indexing.dto.response;

/** Danh mục cho bộ lọc, kèm số sản phẩm đang bán. */
public record CategoryOption(long categoryId, String name, long productCount) {
}
