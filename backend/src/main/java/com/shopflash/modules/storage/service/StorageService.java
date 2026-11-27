package com.shopflash.modules.storage.service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.storage.dto.request.CategorySaveRequest;
import com.shopflash.modules.storage.dto.request.InventoryUpdateRequest;
import com.shopflash.modules.storage.dto.request.ProductCreateRequest;
import com.shopflash.modules.storage.dto.request.ProductSaveRequest;
import com.shopflash.modules.storage.dto.response.CategoryResponse;
import com.shopflash.modules.storage.dto.response.InventoryResponse;
import com.shopflash.modules.storage.dto.response.PageResponse;
import com.shopflash.modules.storage.dto.response.ProductResponse;
import com.shopflash.modules.storage.dto.response.ProjectionRebuildResponse;
import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse;
import com.shopflash.modules.storage.dto.response.RedisConstraintDemoResponse;
import com.shopflash.modules.storage.dto.response.StorageFootprintResponse;
import com.shopflash.modules.storage.model.CatalogProduct;
import com.shopflash.modules.storage.model.Category;
import com.shopflash.modules.storage.model.CategoryStatus;
import com.shopflash.modules.storage.model.Product;
import com.shopflash.modules.storage.model.ProductStatus;
import com.shopflash.modules.storage.repository.ProductProjectionRepository;
import com.shopflash.modules.storage.repository.StorageRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ F1/F2. Oracle commit trước; Redis failure chỉ làm projection stale, không rollback dữ liệu gốc. */
@Service
public class StorageService {

    private static final int REBUILD_BATCH_SIZE = 500;

    private final StorageRepository repository;
    private final ProductProjectionRepository projection;
    private final ProductProjectionSync projectionSync;
    private final ProjectionConsistencyChecker consistency;

    public StorageService(StorageRepository repository, ProductProjectionRepository projection,
            ProductProjectionSync projectionSync, ProjectionConsistencyChecker consistency) {
        this.repository = repository;
        this.projection = projection;
        this.projectionSync = projectionSync;
        this.consistency = consistency;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> categories(int page, int size, String q, CategoryStatus status) {
        int offset = (page - 1) * size;
        List<CategoryResponse> rows = repository.findCategories(offset, size, q, status).stream().map(this::categoryResponse).toList();
        return new PageResponse<>(rows, page, size, repository.countCategories(q, status));
    }

    @Transactional
    public CategoryResponse createCategory(CategorySaveRequest request) {
        String name = requiredText(request.name(), "name");
        String slug = categorySlug(request.slug(), name, null);
        if (repository.categoryNameExists(name, null)) conflict("CATEGORY_NAME_EXISTS", "Tên danh mục đã tồn tại");
        CategoryStatus status = request.status() == null ? CategoryStatus.ACTIVE : request.status();
        long id = repository.createCategory(name, slug, nullableText(request.description()), status);
        return categoryResponse(requireCategory(id));
    }

    @Transactional
    public CategoryResponse updateCategory(long categoryId, CategorySaveRequest request) {
        Category current = requireCategory(categoryId);
        String name = requiredText(request.name(), "name");
        String slug = categorySlug(request.slug(), name, categoryId);
        if (repository.categoryNameExists(name, categoryId)) conflict("CATEGORY_NAME_EXISTS", "Tên danh mục đã tồn tại");
        CategoryStatus nextStatus = request.status() == null ? current.status() : request.status();
        if (nextStatus == CategoryStatus.INACTIVE && current.status() == CategoryStatus.ACTIVE
                && repository.countActiveProductsByCategory(categoryId) > 0) {
            conflict("CATEGORY_HAS_ACTIVE_PRODUCTS", "Hãy ngừng bán các sản phẩm đang hoạt động trước khi ẩn danh mục");
        }
        repository.updateCategory(categoryId, name, slug, nullableText(request.description()), nextStatus);
        scheduleCategoryProjection(categoryId);
        return categoryResponse(requireCategory(categoryId));
    }

    @Transactional
    public void deleteCategory(long categoryId) {
        requireCategory(categoryId);
        if (repository.countProductsByCategory(categoryId) > 0) {
            conflict("CATEGORY_HAS_PRODUCTS", "Không thể xóa danh mục còn sản phẩm");
        }
        repository.deleteCategory(categoryId);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> products(int page, int size, String q, Long categoryId, ProductStatus status) {
        int offset = (page - 1) * size;
        return new PageResponse<>(repository.findProducts(offset, size, q, categoryId, status).stream().map(StorageService::productResponse).toList(),
                page, size, repository.countProducts(q, categoryId, status));
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        Category category = requireCategory(request.categoryId());
        ProductStatus status = request.status() == null ? ProductStatus.ACTIVE : request.status();
        requireActiveCategoryForProduct(category, status);
        String sku = requiredText(request.sku(), "sku");
        String name = requiredText(request.name(), "name");
        String slug = productSlug(request.slug(), name, null);
        if (repository.productSkuExists(sku, null)) conflict("PRODUCT_SKU_EXISTS", "SKU đã tồn tại");
        long id = repository.createProduct(category.categoryId(), sku, name, slug, nullableText(request.description()),
                money(request.basePrice()), nullableText(request.thumbnailUrl()), status, request.initialOnHandQuantity());
        scheduleProductProjection(id);
        return productResponse(requireProduct(id));
    }

    @Transactional
    public ProductResponse updateProduct(long productId, ProductSaveRequest request) {
        requireProduct(productId);
        Category category = requireCategory(request.categoryId());
        ProductStatus status = request.status() == null ? ProductStatus.ACTIVE : request.status();
        requireActiveCategoryForProduct(category, status);
        String sku = requiredText(request.sku(), "sku");
        String name = requiredText(request.name(), "name");
        String slug = productSlug(request.slug(), name, productId);
        if (repository.productSkuExists(sku, productId)) conflict("PRODUCT_SKU_EXISTS", "SKU đã tồn tại");
        repository.updateProduct(productId, category.categoryId(), sku, name, slug, nullableText(request.description()),
                money(request.basePrice()), nullableText(request.thumbnailUrl()), status);
        scheduleProductProjection(productId);
        return productResponse(requireProduct(productId));
    }

    @Transactional
    public ProductResponse updateInventory(long productId, InventoryUpdateRequest request) {
        CatalogProduct current = requireProduct(productId);
        if (request.onHandQuantity() < current.inventory().reservedFlashQuantity()) {
            conflict("INVENTORY_BELOW_RESERVED", "Tồn kho không được thấp hơn số lượng đã giữ cho Flash Sale");
        }
        if (repository.updateInventory(productId, request.onHandQuantity(), request.version()) == 0) {
            CatalogProduct latest = requireProduct(productId);
            if (latest.inventory().version() != request.version()) {
                conflict("INVENTORY_CONFLICT", "Tồn kho vừa được thay đổi, hãy tải lại và thử lại");
            }
            conflict("INVENTORY_UPDATE_FAILED", "Không thể cập nhật tồn kho");
        }
        scheduleProductProjection(productId);
        return productResponse(requireProduct(productId));
    }

    /** Đối chiếu từng Product; không ghi gì. Không đặt transaction readOnly để mỗi lô đọc dữ liệu commit mới nhất. */
    public ProjectionStatusResponse projectionStatus() {
        return consistency.check();
    }

    /** Dựng lại toàn bộ product document, sau đó dọn document không còn Product Oracle. */
    @Transactional(readOnly = true)
    public ProjectionRebuildResponse rebuildProjection() {
        long started = System.nanoTime();
        long afterId = 0;
        long count = 0;
        List<String> keys = new ArrayList<>();
        while (true) {
            List<CatalogProduct> batch = repository.findProjectionBatch(afterId, REBUILD_BATCH_SIZE);
            if (batch.isEmpty()) break;
            projection.upsertAll(batch);
            batch.forEach(product -> keys.add(ProductProjectionRepository.key(product.product().productId())));
            count += batch.size();
            afterId = batch.getLast().product().productId();
        }
        projection.removeNotIn(keys);
        return new ProjectionRebuildResponse(count, Math.round((System.nanoTime() - started) / 1_000_000.0));
    }

    /** Demo Storage Lab không ghi Oracle và luôn dọn key Redis tạm trước khi response. */
    public RedisConstraintDemoResponse runConstraintDemo() {
        ProductProjectionRepository.ConstraintDemo demo = projection.runConstraintDemo();
        return new RedisConstraintDemoResponse(true, true, demo.redisCommand(), demo.storedDocument(), List.of(
                "categoryId = 999999999 không tham chiếu Category nào trong Oracle",
                "price = -1 vi phạm CHECK base_price >= 0 của Oracle"
        ));
    }

    @Transactional(readOnly = true)
    public StorageFootprintResponse storageFootprint() {
        var segments = repository.catalogSegments();
        long oracle = segments.stream().mapToLong(StorageFootprintResponse.OracleSegment::bytes).sum();
        long docs = projection.documentCount();
        return new StorageFootprintResponse(oracle, projection.documentBytes(), docs, segments);
    }

    private CategoryResponse categoryResponse(Category category) {
        return new CategoryResponse(category.categoryId(), category.name(), category.slug(), category.description(), category.status(),
                repository.countProductsByCategory(category.categoryId()), category.createdAt(), category.updatedAt());
    }

    private static ProductResponse productResponse(CatalogProduct value) {
        Product product = value.product();
        var inventory = value.inventory();
        return new ProductResponse(product.productId(), product.categoryId(), value.categoryName(), product.sku(), product.name(),
                product.slug(), product.description(), product.basePrice(), product.thumbnailUrl(), product.status(), value.inStock(),
                new InventoryResponse(inventory.onHandQuantity(), inventory.reservedFlashQuantity(), inventory.normalAvailableQuantity(),
                        inventory.version(), inventory.updatedAt()), product.createdAt(), product.updatedAt());
    }

    private Category requireCategory(long id) {
        Category category = repository.findCategory(id);
        if (category == null) throw ApiException.notFound("CATEGORY_NOT_FOUND", "Không tìm thấy danh mục");
        return category;
    }

    private CatalogProduct requireProduct(long id) {
        CatalogProduct product = repository.findProduct(id);
        if (product == null) throw ApiException.notFound("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm");
        return product;
    }

    private String categorySlug(String requested, String name, Long exceptId) {
        String slug = Slugifier.value(requested, name);
        if (repository.categorySlugExists(slug, exceptId)) conflict("CATEGORY_SLUG_EXISTS", "Slug danh mục đã tồn tại");
        return slug;
    }

    private String productSlug(String requested, String name, Long exceptId) {
        String slug = Slugifier.value(requested, name);
        if (repository.productSlugExists(slug, exceptId)) conflict("PRODUCT_SLUG_EXISTS", "Slug sản phẩm đã tồn tại");
        return slug;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(field, "Trường này là bắt buộc");
        return value.trim();
    }

    private static String nullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw ApiException.badRequest("basePrice", "Giá phải lớn hơn hoặc bằng 0");
        return value;
    }

    private static void requireActiveCategoryForProduct(Category category, ProductStatus status) {
        if (status == ProductStatus.ACTIVE && category.status() != CategoryStatus.ACTIVE) {
            conflict("CATEGORY_INACTIVE", "Không thể bán sản phẩm trong danh mục đang ẩn");
        }
    }

    private void scheduleProductProjection(long productId) {
        projectionSync.afterCommit(List.of(productId));
    }

    private void scheduleCategoryProjection(long categoryId) {
        projectionSync.afterCommitLookup(() -> repository.findProductsForCategory(categoryId).stream()
                .map(product -> product.product().productId()).toList());
    }

    private static void conflict(String code, String message) {
        throw new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
