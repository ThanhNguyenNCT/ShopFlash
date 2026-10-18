package com.shopflash.modules.storage.dto.response;

import java.time.OffsetDateTime;

public record InventoryResponse(long onHandQuantity, long reservedFlashQuantity, long normalAvailableQuantity,
        long version, OffsetDateTime updatedAt) {
}
