package com.shopflash.modules.concurrency.model;

/** Một reservation đọc từ Redis. Trạng thái: PENDING, CLAIMED, CONFIRMED, RELEASED. Giờ là epoch ms. */
public record ReservationView(String reservationId, long saleId, long itemId, long productId, long userId,
                              int quantity, String status, long createdAtMs, long expiresAtMs) {
}
