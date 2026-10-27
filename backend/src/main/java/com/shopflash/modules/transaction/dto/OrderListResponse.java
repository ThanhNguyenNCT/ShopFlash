package com.shopflash.modules.transaction.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderListResponse(int page, int size, long total, List<Summary> items) {
    public record Summary(long orderId, String orderCode, String orderType, String status,
                          OffsetDateTime createdAt, BigDecimal totalAmount, long itemCount) {}
}
