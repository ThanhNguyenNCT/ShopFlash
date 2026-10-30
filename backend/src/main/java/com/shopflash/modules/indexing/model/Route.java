package com.shopflash.modules.indexing.model;

/** Đường đi của một yêu cầu tìm kiếm: có từ khóa thì tìm toàn văn, không có thì duyệt/lọc. */
public enum Route {
    KEYWORD,
    BROWSE
}
