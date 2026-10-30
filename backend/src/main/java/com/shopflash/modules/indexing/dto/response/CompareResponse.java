package com.shopflash.modules.indexing.dto.response;

import java.util.List;

/**
 * Cùng một yêu cầu chạy trên Oracle (AUTO) và Redis (SEARCH).
 * match = tổng số kết quả bằng nhau và trang đầu gồm cùng các productId (không xét thứ tự: khi trùng giá hoặc
 * trùng điểm, mỗi nguồn tự chọn thứ tự).
 */
public record CompareResponse(SearchResponse oracle, SearchResponse redis, boolean match, List<String> mismatches) {
}
