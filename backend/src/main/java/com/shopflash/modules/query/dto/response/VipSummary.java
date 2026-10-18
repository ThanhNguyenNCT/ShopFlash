package com.shopflash.modules.query.dto.response;

import java.math.BigDecimal;

public record VipSummary(BigDecimal averageSpent, long customerCount) {
}
