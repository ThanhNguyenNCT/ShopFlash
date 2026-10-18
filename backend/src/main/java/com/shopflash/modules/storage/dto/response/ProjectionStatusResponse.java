package com.shopflash.modules.storage.dto.response;

public record ProjectionStatusResponse(long oracleCategories, long oracleProducts, long redisDocuments, boolean inSync) {
}
