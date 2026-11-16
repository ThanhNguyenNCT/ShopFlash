package com.shopflash.modules.query.repository.cache;

import com.shopflash.modules.query.dto.response.CategoryRevenueRow;
import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.PageInfo;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.ReportRow;
import com.shopflash.modules.query.dto.response.TopProductRow;
import com.shopflash.modules.query.dto.response.UnsoldProductRow;
import com.shopflash.modules.query.dto.response.VipCustomerRow;
import com.shopflash.modules.query.dto.response.VipSummary;
import com.shopflash.modules.query.model.Report;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * ReportData ↔ JSON lưu trong cache. Kiểu dòng suy từ báo cáo (dòng trong JSON không mang tên kiểu).
 * Đọc số thực thành BigDecimal để tiền giữ nguyên giá trị và scale như Oracle trả.
 */
final class ReportCacheCodec {

    record Entry(ReportData data, OffsetDateTime cachedAt) {
    }

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private ReportCacheCodec() {
    }

    static String encode(ReportData data, OffsetDateTime cachedAt) {
        ObjectNode root = JSON.createObjectNode();
        root.put("cachedAt", cachedAt.toString());
        root.set("rows", JSON.valueToTree(data.rows()));
        root.set("page", JSON.valueToTree(data.page()));
        root.set("summary", JSON.valueToTree(data.summary()));
        return JSON.writeValueAsString(root);
    }

    static Entry decode(Report report, String json) {
        JsonNode root = JSON.readTree(json);
        Class<? extends ReportRow> rowType = rowType(report);
        List<ReportRow> rows = new ArrayList<>();
        for (JsonNode row : root.path("rows")) {
            rows.add(JSON.treeToValue(row, rowType));
        }
        JsonNode page = root.path("page");
        JsonNode summary = root.path("summary");
        ReportData data = new ReportData(rows,
                page.isObject() ? JSON.treeToValue(page, PageInfo.class) : null,
                summary.isObject() && report == Report.VIP_CUSTOMERS ? JSON.treeToValue(summary, VipSummary.class) : null);
        return new Entry(data, OffsetDateTime.parse(root.path("cachedAt").asString()));
    }

    private static Class<? extends ReportRow> rowType(Report report) {
        return switch (report) {
            case REVENUE_BY_MONTH -> MonthRevenueRow.class;
            case REVENUE_BY_CATEGORY -> CategoryRevenueRow.class;
            case TOP_PRODUCTS -> TopProductRow.class;
            case VIP_CUSTOMERS -> VipCustomerRow.class;
            case UNSOLD_PRODUCTS -> UnsoldProductRow.class;
        };
    }
}
