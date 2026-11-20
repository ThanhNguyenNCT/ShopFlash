package com.shopflash.modules.indexing.repository.redis;

import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;
import com.shopflash.modules.indexing.repository.RedisProductSearch;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Tìm sản phẩm trên Redis: một lệnh FT.SEARCH trả cả trang lẫn tổng số. */
@Repository
public class RedisProductSearchRepository implements RedisProductSearch {

    /** Giá trị meta.variant của kết quả Redis (giữ nguyên hợp đồng API). */
    static final String VARIANT = "SEARCH";

    private final IndexingRedis redis;
    private final ObjectMapper json;

    public RedisProductSearchRepository(IndexingRedis redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        String[] args = RedisSearchCommand.search(query);
        long started = System.nanoTime();
        Object reply = redis.call(args);
        RedisSearchCommand.Page page = RedisSearchCommand.parse(reply, json);
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        return new SearchResult(page.items(), page.total(), VARIANT, 1, IndexingRedis.toCli(args), elapsedMs);
    }
}
