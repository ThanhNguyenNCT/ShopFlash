package com.shopflash.modules.query.dto.response;

import java.util.Map;

public record ErrorResponse(Body error) {

    public record Body(String code, String message, Map<String, Object> details) {
    }
}
