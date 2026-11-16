package com.shopflash.modules.concurrency.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LabResetRequest(@NotNull @Min(1) @Max(10000) Integer quota) {
}
