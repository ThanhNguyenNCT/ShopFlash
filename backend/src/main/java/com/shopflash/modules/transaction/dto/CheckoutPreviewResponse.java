package com.shopflash.modules.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

/** Dữ liệu chỉ để xem trước; Checkout luôn kiểm tra lại trong giao dịch ghi. */
public record CheckoutPreviewResponse(long cartVersion, List<Line> lines,
                                      BigDecimal totalAmount, List<String> warnings) {
    public record Line(long productId, String name, String sku, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal, String status,
                       long availableQuantity) {}
}
