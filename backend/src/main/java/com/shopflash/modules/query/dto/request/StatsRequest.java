package com.shopflash.modules.query.dto.request;

import jakarta.validation.constraints.NotNull;

public record StatsRequest(@NotNull Action action) {

    public enum Action { GATHER, DELETE }
}
