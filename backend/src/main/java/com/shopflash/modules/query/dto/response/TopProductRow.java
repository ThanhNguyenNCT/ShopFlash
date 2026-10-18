package com.shopflash.modules.query.dto.response;

import java.math.BigDecimal;

public record TopProductRow(int rank, long productId, String productName, String categoryName, long quantity, BigDecimal revenue) implements ReportRow {

    @Override
    public String key() {
        return String.valueOf(productId);
    }
}
