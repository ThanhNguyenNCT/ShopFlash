package com.shopflash.modules.query.dto.response;

import java.util.List;

public record BenchmarkResult(
        String source,
        String variant,
        int runs,
        double meanMs,
        double stdDevMs,
        double p50Ms,
        double p95Ms,
        int roundTrips,
        List<Double> samplesMs) {
}
