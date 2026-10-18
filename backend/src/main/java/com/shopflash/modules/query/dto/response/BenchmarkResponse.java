package com.shopflash.modules.query.dto.response;

import java.util.List;

public record BenchmarkResponse(String report, List<BenchmarkResult> results) {
}
