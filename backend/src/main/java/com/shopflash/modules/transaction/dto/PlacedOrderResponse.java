package com.shopflash.modules.transaction.dto;

import java.math.BigDecimal;

public record PlacedOrderResponse(long orderId, String orderCode, String status, BigDecimal totalAmount) {}
