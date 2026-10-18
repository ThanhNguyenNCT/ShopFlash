package com.shopflash.modules.storage.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Chỉ Storage được thay số lượng vật lý; Flash Sale reservation thuộc module concurrency. */
public record InventoryUpdateRequest(
        @NotNull(message = "Tồn kho là bắt buộc") @DecimalMin(value = "0", message = "Tồn kho phải lớn hơn hoặc bằng 0") Long onHandQuantity,
        @NotNull(message = "Phiên bản tồn kho là bắt buộc") @DecimalMin(value = "0", message = "Phiên bản tồn kho không hợp lệ") Long version
) {
}
