package com.shopflash.modules.query.model;

public enum Source {
    ORACLE, REDIS;

    public static Source from(String value) {
        return value == null ? ORACLE : valueOf(value.trim().toUpperCase());
    }
}
