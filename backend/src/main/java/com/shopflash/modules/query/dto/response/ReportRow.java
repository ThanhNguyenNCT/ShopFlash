package com.shopflash.modules.query.dto.response;

/** Một dòng báo cáo. key() là khóa so khớp giữa Oracle và Redis trong /compare. */
public sealed interface ReportRow
        permits MonthRevenueRow, CategoryRevenueRow, TopProductRow, VipCustomerRow, UnsoldProductRow {

    String key();
}
