package com.shopflash.modules.indexing.service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.indexing.dto.response.CategoryOption;
import com.shopflash.modules.indexing.dto.response.ProductDetailResponse;
import com.shopflash.modules.indexing.repository.oracle.OracleCatalogRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/** Chi tiết sản phẩm và danh mục cho bộ lọc (luôn đọc Oracle). */
@Service
public class ProductCatalogService {

    private final OracleCatalogRepository catalog;

    public ProductCatalogService(OracleCatalogRepository catalog) {
        this.catalog = catalog;
    }

    public ProductDetailResponse detail(long productId) {
        return catalog.findActiveProduct(productId)
                .orElseThrow(() -> ApiException.notFound("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm"));
    }

    public List<CategoryOption> categories() {
        return catalog.activeCategories();
    }
}
