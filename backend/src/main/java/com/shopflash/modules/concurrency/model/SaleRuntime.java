package com.shopflash.modules.concurrency.model;

/** Phần của chương trình (FLASH_SALES + FLASH_SALE_CONFIGS) cần cho runtime trên Redis. Giờ là epoch ms. */
public record SaleRuntime(long saleId, String status, long startAtMs, long endAtMs, int maxPerUser,
                          int reservationTtlSeconds) {
}
