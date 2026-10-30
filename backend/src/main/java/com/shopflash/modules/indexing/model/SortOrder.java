package com.shopflash.modules.indexing.model;

import java.util.Arrays;
import java.util.Optional;

/** Thứ tự kết quả. RELEVANCE chỉ dùng khi có từ khóa. */
public enum SortOrder {
    RELEVANCE("relevance"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc");

    private final String slug;

    SortOrder(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Optional<SortOrder> fromSlug(String value) {
        return Arrays.stream(values()).filter(s -> s.slug.equalsIgnoreCase(value)).findFirst();
    }
}
