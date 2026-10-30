package com.shopflash.modules.indexing.model;

/**
 * Cách Oracle chạy một yêu cầu (trang Lab).
 * AUTO: optimizer tự chọn, từ khóa tìm bằng Oracle Text (CONTAINS).
 * LIKE: từ khóa tìm bằng UPPER(name) LIKE '%TU%' (không dùng Oracle Text), phần lọc optimizer tự chọn.
 * FULL: như LIKE và thêm hint FULL(p): ép quét toàn bảng, giả lập "không có index".
 */
public enum OracleVariant {
    AUTO,
    LIKE,
    FULL
}
