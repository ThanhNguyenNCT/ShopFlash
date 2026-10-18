package com.shopflash.modules.query.dto.response;

public record Mismatch(String key, String field, Object oracle, Object redis) {
}
