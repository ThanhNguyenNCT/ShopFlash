package com.shopflash.modules.concurrency.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.concurrency.dto.request.FlashItemsRequest;
import com.shopflash.modules.concurrency.dto.request.FlashSaleSaveRequest;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Detail;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.EligibleProduct;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Page;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Rebuild;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Runtime;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Summary;
import com.shopflash.modules.concurrency.service.FlashAdminService;
import com.shopflash.modules.concurrency.service.FlashLifecycleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * F9 phía admin (/api/admin/** đã yêu cầu ADMIN trong SecurityConfig). Contract: AGENTS.md của module, mục
 * "Admin: flash sale".
 */
@RestController
@Validated
@RequestMapping("/api/admin/flash-sales")
public class AdminFlashSaleController {

    private final FlashAdminService admin;
    private final FlashLifecycleService lifecycle;

    public AdminFlashSaleController(FlashAdminService admin, FlashLifecycleService lifecycle) {
        this.admin = admin;
        this.lifecycle = lifecycle;
    }

    @GetMapping
    public Page<Summary> list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page phải từ 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải từ 1")
            @Max(value = 100, message = "size tối đa 100") int size) {
        return admin.page(status, page, size);
    }

    /** Sản phẩm thêm được vào chương trình saleId (ACTIVE, còn hàng, không thuộc chương trình khác trùng giờ). */
    @GetMapping("/eligible-products")
    public Page<EligibleProduct> eligible(@RequestParam long saleId, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page phải từ 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size phải từ 1")
            @Max(value = 100, message = "size tối đa 100") int size) {
        return admin.eligible(saleId, q, page, size);
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable long id) {
        return admin.detail(id);
    }

    @PostMapping
    public ResponseEntity<Detail> create(@Valid @RequestBody FlashSaleSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.create(request));
    }

    @PutMapping("/{id}")
    public Detail update(@PathVariable long id, @Valid @RequestBody FlashSaleSaveRequest request) {
        return admin.update(id, request);
    }

    @PutMapping("/{id}/items")
    public Detail replaceItems(@PathVariable long id, @Valid @RequestBody FlashItemsRequest request) {
        return admin.replaceItems(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        admin.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/schedule")
    public Detail schedule(@PathVariable long id) {
        return lifecycle.schedule(id);
    }

    @PostMapping("/{id}/unschedule")
    public Detail unschedule(@PathVariable long id) {
        return lifecycle.unschedule(id);
    }

    @PostMapping("/{id}/cancel")
    public Detail cancel(@PathVariable long id) {
        return lifecycle.cancel(id);
    }

    @PostMapping("/{id}/stop")
    public Detail stop(@PathVariable long id) {
        return lifecycle.stop(id);
    }

    @GetMapping("/{id}/runtime")
    public Runtime runtime(@PathVariable long id) {
        return lifecycle.runtimeView(id);
    }

    @PostMapping("/{id}/runtime/rebuild")
    public Rebuild rebuild(@PathVariable long id) {
        return lifecycle.rebuild(id);
    }
}
