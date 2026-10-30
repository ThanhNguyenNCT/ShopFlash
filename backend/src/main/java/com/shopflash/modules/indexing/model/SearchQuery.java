package com.shopflash.modules.indexing.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Một yêu cầu tìm kiếm đã kiểm tra hợp lệ (SearchQueryFactory).
 *
 * @param q        chuỗi khách gõ, chỉ để hiển thị
 * @param keywords các từ khóa đã tách (Keywords.parse), rỗng nghĩa là không tìm theo từ
 */
public record SearchQuery(
        String q,
        List<String> keywords,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean inStock,
        SortOrder sort,
        int page,
        int size) {

    public SearchQuery {
        keywords = List.copyOf(keywords);
    }

    public boolean hasKeywords() {
        return !keywords.isEmpty();
    }

    public Route route() {
        return hasKeywords() ? Route.KEYWORD : Route.BROWSE;
    }

    public int offset() {
        return (page - 1) * size;
    }
}
