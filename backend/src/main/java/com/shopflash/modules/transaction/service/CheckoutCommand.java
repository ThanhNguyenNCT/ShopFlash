package com.shopflash.modules.transaction.service;

import java.math.BigDecimal;
import java.util.List;

/** Body đã chuẩn hóa, bất biến trong mọi lần gửi lại cùng Idempotency-Key. */
public record CheckoutCommand(long userId, String key, String hash, long cartVersion,
                              String recipientName, String recipientPhone, String shippingAddress,
                              List<Line> expectedLines) {
    public record Line(long productId, int quantity, BigDecimal unitPrice) {}
}
