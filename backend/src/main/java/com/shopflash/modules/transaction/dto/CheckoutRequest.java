package com.shopflash.modules.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

/** Nội dung khách đã xác nhận; không nhận userId từ trình duyệt. */
public record CheckoutRequest(long cartVersion, String recipientName, String recipientPhone,
                              String shippingAddress, List<ExpectedLine> expectedLines) {
    public record ExpectedLine(long productId, int quantity, BigDecimal unitPrice) {}
}
