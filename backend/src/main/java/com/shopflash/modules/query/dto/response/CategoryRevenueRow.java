package com.shopflash.modules.query.dto.response;

import java.math.BigDecimal;

public record CategoryRevenueRow(long categoryId, String categoryName, long quantity, BigDecimal revenue) implements ReportRow {

    @Override
    public String key() {
        return String.valueOf(categoryId);
    }
}
