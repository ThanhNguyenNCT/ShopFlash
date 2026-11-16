package com.shopflash.modules.concurrency.model;

/** Reservation đã chuyển sang CLAIMED, sẵn sàng ghi đơn trong Oracle. */
public record ClaimedReservation(String reservationId, long saleId, long itemId, long productId, long userId,
                                 int quantity) {
}
