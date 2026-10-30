package com.shopflash.modules.indexing.model;

/**
 * Cách Redis chạy một yêu cầu (trang Lab).
 * SEARCH: FT.SEARCH trên idx:products.
 * SCAN: không dùng index, SCAN product:* + JSON.GET rồi lọc, sắp xếp, phân trang trong Java.
 */
public enum RedisVariant {
    SEARCH,
    SCAN
}
