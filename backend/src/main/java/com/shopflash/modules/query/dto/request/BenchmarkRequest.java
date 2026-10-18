package com.shopflash.modules.query.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Mọi trường đều có thể bỏ trống; BenchmarkService điền mặc định theo contract.
 * variants tối đa 12 phần tử (= BenchmarkService.MAX_VARIANTS), cặp trùng nhau chỉ chạy một lần.
 */
public record BenchmarkRequest(
        LocalDate from,
        LocalDate to,
        @Min(1) @Max(100) Integer limit,
        @Min(0) @Max(5) Integer warmup,
        @Min(1) @Max(30) Integer runs,
        @Size(max = 12) List<@Valid BenchmarkVariant> variants) {

    /** source: oracle | redis; variant: tên JoinHint hoặc RedisMode. */
    public record BenchmarkVariant(String source, String variant) {
    }
}
