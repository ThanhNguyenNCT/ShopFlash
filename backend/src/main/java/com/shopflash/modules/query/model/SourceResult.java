package com.shopflash.modules.query.model;

import com.shopflash.modules.query.dto.response.ReportData;

/**
 * Kết quả một lần chạy báo cáo trên một nguồn. Nguồn không tự đo thời gian: ReportService đo quanh lời gọi run().
 *
 * @param variant    tên JoinHint (Oracle) hoặc RedisMode (Redis) đã dùng
 * @param roundTrips số lần gọi DB qua mạng (một pipeline tính là 1)
 * @param statement  câu SQL giữ nguyên tên bind, hoặc các lệnh Redis, mỗi lệnh một dòng
 */
public record SourceResult(
        ReportData data,
        String variant,
        int roundTrips,
        String statement,
        boolean supported,
        String note) {

    public static SourceResult of(ReportData data, String variant, int roundTrips, String statement) {
        return new SourceResult(data, variant, roundTrips, statement, true, null);
    }

    public static SourceResult unsupported(String variant, String note) {
        return new SourceResult(null, variant, 0, null, false, note);
    }
}
