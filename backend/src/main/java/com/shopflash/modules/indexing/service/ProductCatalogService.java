package com.shopflash.modules.indexing.service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.indexing.dto.response.CategoryOption;
import com.shopflash.modules.indexing.dto.response.IndexInfoResponse;
import com.shopflash.modules.indexing.dto.response.ProductDetailResponse;
import com.shopflash.modules.indexing.repository.RedisProductSearch;
import com.shopflash.modules.indexing.repository.oracle.OracleCatalogRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Chi tiết sản phẩm và danh mục (luôn đọc Oracle), dung lượng index (trang Lab). */
@Service
public class ProductCatalogService {

    private final OracleCatalogRepository catalog;
    private final RedisProductSearch redis;

    public ProductCatalogService(OracleCatalogRepository catalog, RedisProductSearch redis) {
        this.catalog = catalog;
        this.redis = redis;
    }

    public ProductDetailResponse detail(long productId) {
        return catalog.findActiveProduct(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
    }

    public List<CategoryOption> categories() {
        return catalog.activeCategories();
    }

    /** Redis lỗi thì vẫn trả phần Oracle, phần Redis ghi lý do. */
    public IndexInfoResponse indexInfo() {
        Map<String, String> redisInfo;
        try {
            redisInfo = redis.indexInfo();
        } catch (ApiException e) {
            redisInfo = new LinkedHashMap<>();
            redisInfo.put("error", e.code() + ": " + e.getMessage());
        }
        return new IndexInfoResponse(catalog.productIndexSegments(), redisInfo);
    }
}
