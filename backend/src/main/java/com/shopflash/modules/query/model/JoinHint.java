package com.shopflash.modules.query.model;

/** Cách join Oracle được ép bằng hint. AUTO để CBO tự chọn. Hint chỉ lấy từ enum này, không nhận chuỗi từ client. */
public enum JoinHint {
    AUTO, NL, HASH, MERGE
}
