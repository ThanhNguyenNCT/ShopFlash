package com.shopflash.modules.indexing.model;

/** Nguồn dữ liệu trả kết quả tìm kiếm. */
public enum SearchSource {
    ORACLE("oracle"),
    REDIS("redis");

    private final String slug;

    SearchSource(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }
}
