package com.shopflash.modules.indexing.controller;

import com.shopflash.modules.indexing.dto.response.CompareResponse;
import com.shopflash.modules.indexing.dto.response.ExplainResponse;
import com.shopflash.modules.indexing.dto.response.IndexInfoResponse;
import com.shopflash.modules.indexing.dto.response.SearchResponse;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchSource;
import com.shopflash.modules.indexing.repository.OracleProductSearch;
import com.shopflash.modules.indexing.repository.RedisProductSearch;
import com.shopflash.modules.indexing.service.ProductCatalogService;
import com.shopflash.modules.indexing.service.ProductSearchService;
import com.shopflash.modules.indexing.service.SearchQueryFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phòng thí nghiệm Indexing (admin; /api/admin/** đã yêu cầu ADMIN): chạy cùng yêu cầu trên Oracle và Redis,
 * có và không có index, xem kế hoạch thực thi và dung lượng index. Contract: AGENTS.md của module.
 */
@RestController
@RequestMapping("/api/admin/indexing")
public class IndexingLabController {

    private final SearchQueryFactory queries;
    private final ProductSearchService search;
    private final ProductCatalogService catalog;
    private final OracleProductSearch oracle;
    private final RedisProductSearch redis;

    public IndexingLabController(SearchQueryFactory queries, ProductSearchService search,
            ProductCatalogService catalog, OracleProductSearch oracle, RedisProductSearch redis) {
        this.queries = queries;
        this.search = search;
        this.catalog = catalog;
        this.oracle = oracle;
        this.redis = redis;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "minPrice", required = false) String minPrice,
            @RequestParam(name = "maxPrice", required = false) String maxPrice,
            @RequestParam(name = "inStock", required = false) String inStock,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "oracleVariant", required = false) String oracleVariant,
            @RequestParam(name = "redisVariant", required = false) String redisVariant) {
        SearchQuery query = queries.fromParams(q, categoryId, minPrice, maxPrice, inStock, sort, page, size);
        return search.labSearch(query, queries.source(source), queries.oracleVariant(oracleVariant),
                queries.redisVariant(redisVariant));
    }

    @GetMapping("/compare")
    public CompareResponse compare(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "minPrice", required = false) String minPrice,
            @RequestParam(name = "maxPrice", required = false) String maxPrice,
            @RequestParam(name = "inStock", required = false) String inStock,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        return search.compare(queries.fromParams(q, categoryId, minPrice, maxPrice, inStock, sort, page, size));
    }

    /** source bắt buộc: oracle, redis, hoặc auto (có từ khóa thì redis, không thì oracle). */
    @GetMapping("/explain")
    public ExplainResponse explain(
            @RequestParam(name = "source") String source,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam(name = "minPrice", required = false) String minPrice,
            @RequestParam(name = "maxPrice", required = false) String maxPrice,
            @RequestParam(name = "inStock", required = false) String inStock,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size,
            @RequestParam(name = "oracleVariant", required = false) String oracleVariant,
            @RequestParam(name = "redisVariant", required = false) String redisVariant) {
        SearchQuery query = queries.fromParams(q, categoryId, minPrice, maxPrice, inStock, sort, page, size);
        SearchSource src = queries.source(source).orElse(query.hasKeywords() ? SearchSource.REDIS : SearchSource.ORACLE);
        return src == SearchSource.ORACLE
                ? oracle.explain(query, queries.oracleVariant(oracleVariant))
                : redis.explain(query, queries.redisVariant(redisVariant));
    }

    @GetMapping("/index-info")
    public IndexInfoResponse indexInfo() {
        return catalog.indexInfo();
    }
}
