package com.shopflash.modules.query.dto.response;

import java.time.OffsetDateTime;

public record AnalyticsStatusResponse(OracleSide oracle, RedisSide redis, boolean inSync) {

    /** Chỉ đếm đơn CONFIRMED/COMPLETED và dòng hàng của các đơn đó. */
    public record OracleSide(long countedOrders, long orderItems) {
    }

    public record RedisSide(
            long orderDocs, long lineDocs, boolean indexing, OffsetDateTime lastRebuildAt, Double lastRebuildMs) {
    }
}
