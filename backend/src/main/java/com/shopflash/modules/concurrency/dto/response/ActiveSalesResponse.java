package com.shopflash.modules.concurrency.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/flash-sales/active. phase: UPCOMING (chưa tới startAt) hoặc LIVE. remaining lấy từ Redis; chương trình
 * chưa nạp runtime thì lấy allocated - sold trong Oracle.
 */
public record ActiveSalesResponse(OffsetDateTime serverTime, List<Sale> sales) {

    public record Sale(long flashSaleId, String name, String description, String phase, OffsetDateTime startAt,
                       OffsetDateTime endAt, int maxQuantityPerUser, int reservationTimeoutSeconds,
                       List<Item> items) {
    }

    public record Item(long itemId, long productId, String productName, String thumbnailUrl, BigDecimal basePrice,
                       BigDecimal salePrice, long allocated, long remaining) {
    }
}
