package com.shopflash.modules.indexing.controller;

import com.shopflash.modules.indexing.dto.response.CategoryOption;
import com.shopflash.modules.indexing.dto.response.ProductDetailResponse;
import com.shopflash.modules.indexing.dto.response.SearchResponse;
import com.shopflash.modules.indexing.service.ProductCatalogService;
import com.shopflash.modules.indexing.service.ProductSearchService;
import com.shopflash.modules.indexing.service.SearchQueryFactory;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API công khai cho khách (F3, F4), chỉ GET; SecurityConfig mở /api/products/** cho mọi người.
 * Tham số nhận dạng chuỗi rồi SearchQueryFactory kiểm tra, để lỗi có cùng định dạng và thông báo tiếng Việt.
 * Contract: AGENTS.md của module.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final SearchQueryFactory queries;
    private final ProductSearchService search;
    private final ProductCatalogService catalog;

    public ProductController(SearchQueryFactory queries, ProductSearchService search, ProductCatalogService catalog) {
        this.queries = queries;
        this.search = search;
        this.catalog = catalog;
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
            @RequestParam(name = "size", required = false) String size) {
        return search.search(queries.fromParams(q, categoryId, minPrice, maxPrice, inStock, sort, page, size));
    }

    /** Đường dẫn cố định /categories được Spring ưu tiên hơn /{productId}. */
    @GetMapping("/categories")
    public List<CategoryOption> categories() {
        return catalog.categories();
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable("productId") long productId) {
        return catalog.detail(productId);
    }
}
