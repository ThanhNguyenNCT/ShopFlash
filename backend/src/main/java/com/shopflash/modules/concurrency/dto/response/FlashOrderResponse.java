package com.shopflash.modules.concurrency.dto.response;

import java.math.BigDecimal;

/** Giống PlacedOrderResponse của module transaction để frontend dùng chung màn hình "Đặt hàng thành công". */
public record FlashOrderResponse(long orderId, String orderCode, String status, BigDecimal totalAmount) {
}
