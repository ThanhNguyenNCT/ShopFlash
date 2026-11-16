package com.shopflash.modules.concurrency.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Suất đã giữ. status: PENDING, CLAIMED, CONFIRMED, RELEASED. serverTime để frontend đếm ngược tới expiresAt. */
public record ReservationResponse(String reservationId, long flashSaleId, long itemId, long productId,
                                  String productName, String thumbnailUrl, int quantity, BigDecimal salePrice,
                                  BigDecimal totalAmount, String status, OffsetDateTime expiresAt,
                                  OffsetDateTime serverTime) {
}
