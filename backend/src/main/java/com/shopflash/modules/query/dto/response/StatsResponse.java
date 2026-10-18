package com.shopflash.modules.query.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record StatsResponse(String action, List<TableStats> tables, double elapsedMs) {

    public record TableStats(String table, Long numRows, OffsetDateTime lastAnalyzed) {
    }
}
