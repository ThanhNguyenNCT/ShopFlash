package com.shopflash.modules.concurrency.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/flash-sales/{flashSaleId}/stock, chỉ đọc Redis. */
public record StockResponse(OffsetDateTime serverTime, List<ItemStock> items) {

    public record ItemStock(long itemId, long remaining) {
    }
}
