package com.shopflash.modules.concurrency.dto.response;

import java.util.List;

import com.shopflash.modules.concurrency.lab.LabStats.RunMetrics;
import com.shopflash.modules.concurrency.lab.LabStats.Summary;
import com.shopflash.modules.concurrency.lab.LabStrategy;

/**
 * Kết quả POST /api/admin/concurrency-lab/runs. config ghi lại tham số và kích thước pool để báo cáo ghi kèm số
 * đo. Mỗi chiến lược có summary (trung bình ± độ lệch chuẩn qua các lần đo) và từng lần đo (không gồm warmup).
 */
public record LabRunResponse(Config config, List<StrategyResult> results) {

    public record Config(
            int buyers,
            int quota,
            int concurrency,
            int runs,
            int warmup,
            int oraclePoolSize,
            int redisPoolSize,
            int maxRetries) {
    }

    public record StrategyResult(
            LabStrategy strategy,
            LabStrategy.Store store,
            Summary summary,
            List<RunMetrics> runs) {
    }
}
