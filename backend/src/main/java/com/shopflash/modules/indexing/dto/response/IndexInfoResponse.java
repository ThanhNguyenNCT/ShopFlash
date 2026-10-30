package com.shopflash.modules.indexing.dto.response;

import java.util.List;
import java.util.Map;

/** Dung lượng index: segment Oracle của bảng PRODUCTS (B-tree và bảng phụ Oracle Text) và FT.INFO idx:products. */
public record IndexInfoResponse(List<OracleSegment> oracle, Map<String, String> redis) {

    public record OracleSegment(String segmentName, String segmentType, String belongsTo, long bytes) {
    }
}
