package com.shopflash.modules.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

/** Giá và thành tiền chỉ là thông tin tham khảo; Checkout sẽ kiểm tra lại. */
public record CartResponse(long version, List<Item> items, BigDecimal totalAmount) {
    public record Item(long productId, String name, String sku, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal, String thumbnailUrl) {}
}
