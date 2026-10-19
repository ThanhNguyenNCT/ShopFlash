package com.shopflash.modules.storage.controller;

import com.shopflash.modules.storage.dto.request.CategorySaveRequest;
import com.shopflash.modules.storage.dto.request.InventoryUpdateRequest;
import com.shopflash.modules.storage.dto.request.ProductCreateRequest;
import com.shopflash.modules.storage.dto.request.ProductSaveRequest;
import com.shopflash.modules.storage.dto.response.CategoryResponse;
import com.shopflash.modules.storage.dto.response.PageResponse;
import com.shopflash.modules.storage.dto.response.ProductResponse;
import com.shopflash.modules.storage.dto.response.ProjectionRebuildResponse;
import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse;
import com.shopflash.modules.storage.dto.response.RedisConstraintDemoResponse;
import com.shopflash.modules.storage.dto.response.StorageFootprintResponse;
import com.shopflash.modules.storage.model.CategoryStatus;
import com.shopflash.modules.storage.model.ProductStatus;
import com.shopflash.modules.storage.service.StorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** F1/F2 CRUD và Storage Lab. Chưa có F0 nên /api/admin/** chỉ bind localhost. */
@RestController
@RequestMapping("/api/admin")
public class StorageController {

    private final StorageService service;

    public StorageController(StorageService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public PageResponse<CategoryResponse> categories(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page phải từ 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải từ 1") @Max(value = 100, message = "size tối đa 100") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) CategoryStatus status) {
        return service.categories(page, size, q, status);
    }

    @PostMapping(path = "/categories", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CategoryResponse createCategory(@Valid @RequestBody CategorySaveRequest request) {
        return service.createCategory(request);
    }

    @PutMapping(path = "/categories/{categoryId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CategoryResponse updateCategory(@PathVariable long categoryId, @Valid @RequestBody CategorySaveRequest request) {
        return service.updateCategory(categoryId, request);
    }

    @DeleteMapping("/categories/{categoryId}")
    public void deleteCategory(@PathVariable long categoryId) {
        service.deleteCategory(categoryId);
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page phải từ 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải từ 1") @Max(value = 100, message = "size tối đa 100") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ProductStatus status) {
        return service.products(page, size, q, categoryId, status);
    }

    @PostMapping(path = "/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return service.createProduct(request);
    }

    @PutMapping(path = "/products/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse updateProduct(@PathVariable long productId, @Valid @RequestBody ProductSaveRequest request) {
        return service.updateProduct(productId, request);
    }

    @PatchMapping(path = "/products/{productId}/inventory", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProductResponse updateInventory(@PathVariable long productId, @Valid @RequestBody InventoryUpdateRequest request) {
        return service.updateInventory(productId, request);
    }

    @GetMapping("/catalog/projection/status")
    public ProjectionStatusResponse projectionStatus() {
        return service.projectionStatus();
    }

    @PostMapping("/catalog/projection/rebuild")
    public ProjectionRebuildResponse rebuildProjection() {
        return service.rebuildProjection();
    }

    @PostMapping("/catalog/constraint-demo")
    public RedisConstraintDemoResponse runConstraintDemo() {
        return service.runConstraintDemo();
    }

    @GetMapping("/catalog/storage-footprint")
    public StorageFootprintResponse storageFootprint() {
        return service.storageFootprint();
    }
}
