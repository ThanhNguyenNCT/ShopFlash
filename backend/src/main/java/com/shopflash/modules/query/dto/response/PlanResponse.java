package com.shopflash.modules.query.dto.response;

import java.util.List;

public record PlanResponse(String source, String variant, String statement, List<String> lines) {
}
