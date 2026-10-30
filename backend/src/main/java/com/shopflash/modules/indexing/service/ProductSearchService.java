package com.shopflash.modules.indexing.service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.indexing.dto.response.CompareResponse;
import com.shopflash.modules.indexing.dto.response.PageInfo;
import com.shopflash.modules.indexing.dto.response.ProductItem;
import com.shopflash.modules.indexing.dto.response.SearchData;
import com.shopflash.modules.indexing.dto.response.SearchMeta;
import com.shopflash.modules.indexing.dto.response.SearchResponse;
import com.shopflash.modules.indexing.model.OracleVariant;
import com.shopflash.modules.indexing.model.RedisVariant;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;
import com.shopflash.modules.indexing.model.SearchSource;
import com.shopflash.modules.indexing.repository.OracleProductSearch;
import com.shopflash.modules.indexing.repository.RedisProductSearch;
import com.shopflash.modules.indexing.repository.redis.IndexingRedis;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Định tuyến tìm kiếm (AGENTS.md, report/indexing/ket-luan.md §3): mỗi yêu cầu đi tới đúng một nguồn.
 * Có từ khóa → RediSearch (E2: nhanh nhất cho tìm từ). Không có từ khóa → Oracle, index ghép (E3).
 * Có từ khóa mà Redis không truy cập được hoặc chưa có index → Oracle Text, meta.note ghi lý do.
 */
@Service
public class ProductSearchService {

    public static final ZoneOffset ZONE = ZoneOffset.ofHours(7);
    static final String FALLBACK_NOTE = "Redis không truy cập được, đã tìm bằng Oracle Text";

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final OracleProductSearch oracle;
    private final RedisProductSearch redis;
    private final Clock clock;

    @Autowired
    public ProductSearchService(OracleProductSearch oracle, RedisProductSearch redis) {
        this(oracle, redis, Clock.system(ZONE));
    }

    public ProductSearchService(OracleProductSearch oracle, RedisProductSearch redis, Clock clock) {
        this.oracle = oracle;
        this.redis = redis;
        this.clock = clock;
    }

    /** API công khai. */
    public SearchResponse search(SearchQuery query) {
        if (!query.hasKeywords()) {
            return response(query, SearchSource.ORACLE, oracle.search(query, OracleVariant.AUTO), null);
        }
        try {
            return response(query, SearchSource.REDIS, redis.search(query, RedisVariant.SEARCH), null);
        } catch (ApiException e) {
            if (!isRedisDown(e)) {
                throw e;
            }
            log.warn("Tìm kiếm Redis lỗi {}, chuyển sang Oracle Text", e.code());
            return response(query, SearchSource.ORACLE, oracle.search(query, OracleVariant.AUTO), FALLBACK_NOTE);
        }
    }

    /** Trang Lab: chọn nguồn và biến thể; không chọn nguồn thì định tuyến như API công khai. */
    public SearchResponse labSearch(SearchQuery query, Optional<SearchSource> source, OracleVariant oracleVariant,
            RedisVariant redisVariant) {
        if (source.isEmpty()) {
            return search(query);
        }
        return source.get() == SearchSource.ORACLE
                ? response(query, SearchSource.ORACLE, oracle.search(query, oracleVariant), null)
                : response(query, SearchSource.REDIS, redis.search(query, redisVariant), null);
    }

    /** Cùng yêu cầu trên Oracle (AUTO) và Redis (SEARCH); so tổng số và tập productId của trang. */
    public CompareResponse compare(SearchQuery query) {
        SearchResponse o = response(query, SearchSource.ORACLE, oracle.search(query, OracleVariant.AUTO), null);
        SearchResponse r = response(query, SearchSource.REDIS, redis.search(query, RedisVariant.SEARCH), null);
        List<String> mismatches = new ArrayList<>();
        if (o.meta().total() != r.meta().total()) {
            mismatches.add("Tổng số khác nhau: Oracle " + o.meta().total() + ", Redis " + r.meta().total());
        }
        Set<Long> oIds = ids(o.data().items());
        Set<Long> rIds = ids(r.data().items());
        Set<Long> onlyOracle = new LinkedHashSet<>(oIds);
        onlyOracle.removeAll(rIds);
        Set<Long> onlyRedis = new LinkedHashSet<>(rIds);
        onlyRedis.removeAll(oIds);
        if (!onlyOracle.isEmpty()) {
            mismatches.add("Chỉ có trong trang Oracle: " + limit(onlyOracle));
        }
        if (!onlyRedis.isEmpty()) {
            mismatches.add("Chỉ có trong trang Redis: " + limit(onlyRedis));
        }
        return new CompareResponse(o, r, mismatches.isEmpty(), mismatches);
    }

    static boolean isRedisDown(ApiException e) {
        return IndexingRedis.UNAVAILABLE.equals(e.code()) || IndexingRedis.INDEX_MISSING.equals(e.code());
    }

    SearchResponse response(SearchQuery query, SearchSource source, SearchResult result, String note) {
        SearchData data = new SearchData(result.items(), new PageInfo(query.page(), query.size(), result.total()));
        SearchMeta meta = new SearchMeta(source.slug(), query.route().name(), result.variant(),
                Math.round(result.elapsedMs() * 1000) / 1000.0, result.roundTrips(), result.statement(),
                result.total(), result.items().size(), OffsetDateTime.now(clock), note);
        return new SearchResponse(data, meta);
    }

    private static Set<Long> ids(List<ProductItem> items) {
        Set<Long> out = new LinkedHashSet<>();
        items.forEach(i -> out.add(i.productId()));
        return out;
    }

    private static String limit(Set<Long> ids) {
        return ids.stream().limit(10).map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("")
                + (ids.size() > 10 ? " ..." : "");
    }
}
