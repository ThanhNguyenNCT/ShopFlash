package com.shopflash.modules.storage.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Kết quả đối chiếu projection theo từng Product ID. {@code checkState = FAILED} khi một DBMS không đọc được: lúc đó
 * các số đếm là null và {@code inSync = false}, nghĩa là chưa xác định, không phải đã đồng bộ.
 */
public record ProjectionStatusResponse(
        String checkState,
        String failureCode,
        String failureMessage,
        OffsetDateTime checkedAt,
        long elapsedMs,
        Long oracleCategories,
        Long oracleProducts,
        Long redisDocuments,
        Long matched,
        Long missing,
        Long extra,
        Long mismatched,
        Long invalid,
        boolean inSync,
        int issueLimit,
        List<Issue> issues
) {
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /** kind: MISSING, EXTRA, MISMATCH hoặc INVALID. */
    public record Issue(String kind, Long productId, String key, String message, List<FieldDiff> fields) {
    }

    public record FieldDiff(String field, Object oracle, Object redis) {
    }
}
