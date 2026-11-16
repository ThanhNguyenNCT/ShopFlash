package com.shopflash.modules.concurrency.dto.request;

/**
 * Thông tin nhận hàng. Kiểm tra giống Checkout của module transaction (FlashCheckoutService.recipient): chuẩn hóa
 * NFC, bỏ khoảng trắng hai đầu, không rỗng, không quá số byte của cột Oracle (150, 30, 500).
 */
public record FlashCheckoutRequest(String recipientName, String recipientPhone, String shippingAddress) {
}
