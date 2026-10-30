package com.shopflash.modules.indexing.model;

import com.shopflash.modules.indexing.dto.response.ProductItem;
import java.util.List;

/** Kết quả thô của một nguồn: trang sản phẩm, tổng số, và cách đã chạy (cho meta). */
public record SearchResult(
        List<ProductItem> items,
        long total,
        String variant,
        int roundTrips,
        String statement,
        double elapsedMs) {

    public SearchResult {
        items = List.copyOf(items);
    }
}
