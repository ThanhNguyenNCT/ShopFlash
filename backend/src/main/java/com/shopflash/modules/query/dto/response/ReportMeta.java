package com.shopflash.modules.query.dto.response;

import java.time.OffsetDateTime;

public record ReportMeta(
        String source,
        String report,
        String variant,
        boolean supported,
        double elapsedMs,
        int roundTrips,
        String statement,
        int rowCount,
        OffsetDateTime generatedAt,
        String note) {
}
