package com.shopflash.modules.query.model;

public enum Source {
    ORACLE, REDIS, CACHE;

    public static Source from(String value) {
        return value == null ? ORACLE : valueOf(value.trim().toUpperCase());
    }
}
