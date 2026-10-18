package com.shopflash.modules.query.dto.response;

import java.util.List;

/** match là null khi một bên không hỗ trợ báo cáo. */
public record CompareResponse(
        String report, ReportResponse oracle, ReportResponse redis, Boolean match, List<Mismatch> mismatches) {
}
