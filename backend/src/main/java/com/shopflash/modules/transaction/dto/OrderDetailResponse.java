package com.shopflash.modules.transaction.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderDetailResponse(long orderId, String orderCode, String orderType, String status,
                                  String recipientName, String recipientPhone, String shippingAddress,
                                  BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount,
                                  OffsetDateTime createdAt, OffsetDateTime cancelledAt, List<Line> items) {
    public record Line(long orderItemId, long productId, String productName, String productSku,
                       BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
}
