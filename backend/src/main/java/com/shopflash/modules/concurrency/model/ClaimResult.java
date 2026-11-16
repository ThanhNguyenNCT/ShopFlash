package com.shopflash.modules.concurrency.model;

/**
 * Kết quả bước claim. alreadyConfirmed = true khi lần đặt đơn trước đã xong (client gửi lại sau khi mất phản hồi):
 * khi đó reservation là null và service đọc lại đơn theo idempotency key.
 */
public record ClaimResult(boolean alreadyConfirmed, ClaimedReservation reservation) {

    public static ClaimResult claimed(ClaimedReservation reservation) {
        return new ClaimResult(false, reservation);
    }

    public static ClaimResult confirmedBefore() {
        return new ClaimResult(true, null);
    }
}
