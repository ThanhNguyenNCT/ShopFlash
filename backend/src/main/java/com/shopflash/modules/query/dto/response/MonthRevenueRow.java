package com.shopflash.modules.query.dto.response;

import java.math.BigDecimal;

public record MonthRevenueRow(String month, long orderCount, BigDecimal revenue) implements ReportRow {

    @Override
    public String key() {
        return month;
    }
}
