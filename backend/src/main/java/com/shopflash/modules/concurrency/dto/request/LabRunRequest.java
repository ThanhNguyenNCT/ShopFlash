package com.shopflash.modules.concurrency.dto.request;

import java.util.List;

import com.shopflash.modules.concurrency.lab.LabStrategy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Mọi trường đều có thể bỏ trống; ConcurrencyLabService điền mặc định: mọi chiến lược, buyers 1000, quota 100,
 * concurrency 64, runs 5, warmup 1. quota phải <= buyers (kiểm tra ở service).
 */
public record LabRunRequest(
        @Size(max = 10) List<LabStrategy> strategies,
        @Min(1) @Max(10000) Integer buyers,
        @Min(1) @Max(10000) Integer quota,
        @Min(1) @Max(256) Integer concurrency,
        @Min(1) @Max(10) Integer runs,
        @Min(0) @Max(3) Integer warmup) {
}
