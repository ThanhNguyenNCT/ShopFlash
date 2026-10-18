package com.shopflash.modules.query.dto.response;

import java.math.BigDecimal;

public record VipCustomerRow(long userId, String fullName, String email, long orderCount, BigDecimal totalSpent) implements ReportRow {

    @Override
    public String key() {
        return String.valueOf(userId);
    }
}
