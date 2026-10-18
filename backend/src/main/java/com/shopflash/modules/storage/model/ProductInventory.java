package com.shopflash.modules.storage.model;

import java.time.OffsetDateTime;

/** Bản ghi domain ánh xạ bảng PRODUCT_INVENTORY. */
public record ProductInventory(
        long productId,
        long onHandQuantity,
        long reservedFlashQuantity,
        long version,
        OffsetDateTime updatedAt
) {
    /** Số lượng còn được bán theo luồng thông thường, không gồm suất Flash Sale đã giữ. */
    public long normalAvailableQuantity() {
        return onHandQuantity - reservedFlashQuantity;
    }
}
