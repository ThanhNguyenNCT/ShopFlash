package com.shopflash.modules.indexing.repository;

import com.shopflash.modules.indexing.dto.response.ExplainResponse;
import com.shopflash.modules.indexing.model.OracleVariant;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;

/** Tìm sản phẩm trên Oracle. Tách interface để service test được bằng bản giả. */
public interface OracleProductSearch {

    SearchResult search(SearchQuery query, OracleVariant variant);

    default ExplainResponse explain(SearchQuery query, OracleVariant variant) {
        throw new UnsupportedOperationException("explain");
    }
}
