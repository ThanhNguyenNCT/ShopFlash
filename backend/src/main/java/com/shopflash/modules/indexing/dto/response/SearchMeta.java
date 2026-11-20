package com.shopflash.modules.indexing.dto.response;

import java.time.OffsetDateTime;

/**
 * Cách một yêu cầu đã được chạy, để console demo và báo cáo thấy rõ nguồn, index và chi phí.
 *
 * @param route      KEYWORD (có từ khóa) hoặc BROWSE
 * @param variant    cách đã chạy: AUTO (Oracle) hoặc SEARCH (Redis)
 * @param elapsedMs  thời gian gọi DB và đọc kết quả, không tính serialize HTTP
 * @param roundTrips số lần gửi lệnh/câu SQL tới DB
 * @param statement  câu SQL (giữ tên bind) hoặc lệnh Redis đã gửi
 * @param note       ghi chú, ví dụ khi Redis lỗi và đã chuyển sang Oracle
 */
public record SearchMeta(
        String source,
        String route,
        String variant,
        double elapsedMs,
        int roundTrips,
        String statement,
        long total,
        int rowCount,
        OffsetDateTime generatedAt,
        String note) {
}
