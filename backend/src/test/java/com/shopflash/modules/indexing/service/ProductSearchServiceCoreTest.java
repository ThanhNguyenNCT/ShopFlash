package com.shopflash.modules.indexing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.indexing.dto.response.ProductItem;
import com.shopflash.modules.indexing.dto.response.SearchResponse;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;
import com.shopflash.modules.indexing.model.SortOrder;
import com.shopflash.modules.indexing.repository.OracleProductSearch;
import com.shopflash.modules.indexing.repository.RedisProductSearch;
import com.shopflash.modules.indexing.repository.redis.IndexingRedis;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProductSearchServiceCoreTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), ProductSearchService.ZONE);

    static final SearchQuery KEYWORD = new SearchQuery("sony", List.of("sony"), null, null, null, false,
            SortOrder.RELEVANCE, 1, 24);
    static final SearchQuery BROWSE = new SearchQuery(null, List.of(), 5L, null, null, false,
            SortOrder.PRICE_ASC, 1, 24);

    static ProductItem item(long id) {
        return new ProductItem(id, "S" + id, "P" + id, 5L, "C", BigDecimal.TEN, true, null);
    }

    static SearchResult result(String variant, long total, long... ids) {
        List<ProductItem> items = new ArrayList<>();
        for (long id : ids) {
            items.add(item(id));
        }
        return new SearchResult(items, total, variant, 1, "STMT " + variant, 1.23456);
    }

    static final class FakeOracle implements OracleProductSearch {
        int calls;
        SearchResult next = result("AUTO", 2, 1, 2);

        @Override
        public SearchResult search(SearchQuery query) {
            calls++;
            return next;
        }
    }

    static final class FakeRedis implements RedisProductSearch {
        int calls;
        SearchResult next = result("SEARCH", 2, 1, 2);
        RuntimeException error;

        @Override
        public SearchResult search(SearchQuery query) {
            calls++;
            if (error != null) {
                throw error;
            }
            return next;
        }
    }

    final FakeOracle oracle = new FakeOracle();
    final FakeRedis redis = new FakeRedis();
    final ProductSearchService service = new ProductSearchService(oracle, redis, CLOCK);

    @Test
    void keywordGoesToRedis() {
        SearchResponse res = service.search(KEYWORD);
        assertThat(redis.calls).isEqualTo(1);
        assertThat(oracle.calls).isZero();
        assertThat(res.meta().source()).isEqualTo("redis");
        assertThat(res.meta().route()).isEqualTo("KEYWORD");
        assertThat(res.meta().note()).isNull();
        assertThat(res.meta().elapsedMs()).isEqualTo(1.235);
        assertThat(res.meta().rowCount()).isEqualTo(2);
        assertThat(res.data().page().total()).isEqualTo(2);
        assertThat(res.meta().generatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
    }

    @Test
    void browseGoesToOracle() {
        SearchResponse res = service.search(BROWSE);
        assertThat(oracle.calls).isEqualTo(1);
        assertThat(redis.calls).isZero();
        assertThat(res.meta().source()).isEqualTo("oracle");
        assertThat(res.meta().route()).isEqualTo("BROWSE");
    }

    @Test
    void keywordFallsBackToOracleTextWhenRedisIsDown() {
        redis.error = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, IndexingRedis.UNAVAILABLE, "down");
        SearchResponse res = service.search(KEYWORD);
        assertThat(oracle.calls).isEqualTo(1);
        assertThat(res.meta().source()).isEqualTo("oracle");
        assertThat(res.meta().route()).isEqualTo("KEYWORD");
        assertThat(res.meta().note()).isEqualTo(ProductSearchService.FALLBACK_NOTE);

        redis.error = new ApiException(HttpStatus.SERVICE_UNAVAILABLE, IndexingRedis.INDEX_MISSING, "no index");
        assertThat(service.search(KEYWORD).meta().source()).isEqualTo("oracle");
    }

    @Test
    void otherRedisErrorsAreNotHidden() {
        redis.error = new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_ERROR", "bad");
        assertThatThrownBy(() -> service.search(KEYWORD)).isSameAs(redis.error);
        assertThat(oracle.calls).isZero();
    }
}
