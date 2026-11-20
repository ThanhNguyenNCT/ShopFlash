package com.shopflash.modules.indexing.repository;

import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;

/** Tìm sản phẩm trên Redis (projection product:*, index idx:products). Tách interface để service test được. */
public interface RedisProductSearch {

    SearchResult search(SearchQuery query);
}
