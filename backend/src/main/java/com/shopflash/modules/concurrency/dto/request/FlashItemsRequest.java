package com.shopflash.modules.concurrency.dto.request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/admin/flash-sales/{id}/items: thay toàn bộ danh sách sản phẩm của chương trình DRAFT. version là version
 * của chương trình mà admin đang xem; đã đổi thì 409 FLASH_SALE_CHANGED.
 */
public record FlashItemsRequest(
        @NotNull Long version,
        @NotNull @Size(max = 50) List<@Valid @NotNull Item> items) {

    public record Item(
            @NotNull @Min(1) Long productId,
            @NotNull @DecimalMin("0") BigDecimal salePrice,
            @NotNull @Min(1) @Max(100000) Integer allocatedQuantity) {
    }
}
