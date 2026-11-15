package com.shopflash.modules.concurrency.repository.redis;

/** Tên key Redis của runtime flash sale (bảng "Redis" trong AGENTS.md của module). */
public final class FlashKeys {

    /** ZSET: score = expiresAt (epoch ms), member = reservationId. */
    public static final String EXPIRY = "flash:reservations:expiry";

    private FlashKeys() {
    }

    public static String sale(long saleId) {
        return "flash:sale:" + saleId;
    }

    public static String item(long itemId) {
        return "flash:item:" + itemId;
    }

    public static String userQty(long itemId) {
        return "flash:item:" + itemId + ":user_qty";
    }

    public static String pending(long itemId) {
        return "flash:item:" + itemId + ":pending";
    }

    public static String reservation(String reservationId) {
        return "flash:reservation:" + reservationId;
    }
}
