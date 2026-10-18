package com.shopflash.modules.query.dto.response;

import java.math.BigDecimal;

public record UnsoldProductRow(long productId, String productName, String categoryName, BigDecimal basePrice) implements ReportRow {

    @Override
    public String key() {
        return String.valueOf(productId);
    }
}
