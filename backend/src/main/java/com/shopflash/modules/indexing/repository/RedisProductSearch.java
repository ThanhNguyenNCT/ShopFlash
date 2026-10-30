package com.shopflash.modules.indexing.repository;

import com.shopflash.modules.indexing.dto.response.ExplainResponse;
import com.shopflash.modules.indexing.model.RedisVariant;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;
import java.util.Map;

/** Tìm sản phẩm trên Redis (projection product:*, index idx:products). Tách interface để service test được. */
public interface RedisProductSearch {

    SearchResult search(SearchQuery query, RedisVariant variant);

    default ExplainResponse explain(SearchQuery query, RedisVariant variant) {
        throw new UnsupportedOperationException("explain");
    }

    default Map<String, String> indexInfo() {
        throw new UnsupportedOperationException("indexInfo");
    }
}
