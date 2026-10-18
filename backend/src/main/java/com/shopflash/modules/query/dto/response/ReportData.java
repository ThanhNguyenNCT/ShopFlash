package com.shopflash.modules.query.dto.response;

import java.util.List;

/** page chỉ có ở báo cáo phân trang; summary chỉ có ở vip-customers (VipSummary). */
public record ReportData(List<? extends ReportRow> rows, PageInfo page, Object summary) {

    public static ReportData of(List<? extends ReportRow> rows) {
        return new ReportData(rows, null, null);
    }
}
