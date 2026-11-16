package com.shopflash.modules.concurrency.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.concurrency.dto.request.FlashCheckoutRequest;
import com.shopflash.modules.concurrency.dto.request.ReserveRequest;
import com.shopflash.modules.concurrency.dto.response.ActiveSalesResponse;
import com.shopflash.modules.concurrency.dto.response.FlashOrderResponse;
import com.shopflash.modules.concurrency.dto.response.ReservationResponse;
import com.shopflash.modules.concurrency.dto.response.StockResponse;
import com.shopflash.modules.concurrency.service.FlashCheckoutService;
import com.shopflash.modules.concurrency.service.FlashCheckoutService.Placed;
import com.shopflash.modules.concurrency.service.FlashSaleService;
import com.shopflash.modules.concurrency.service.ReservationService;
import com.shopflash.modules.concurrency.service.ReservationService.Reserved;

import jakarta.validation.Valid;

/**
 * F9 phía khách. Quyền đặt trong SecurityConfig: active và stock cho mọi người, còn lại cần CUSTOMER.
 * Contract: AGENTS.md của module, mục "Khách: flash sale".
 */
@RestController
@RequestMapping("/api/flash-sales")
public class FlashSaleController {

    private final FlashSaleService sales;
    private final ReservationService reservations;
    private final FlashCheckoutService checkout;

    public FlashSaleController(FlashSaleService sales, ReservationService reservations,
            FlashCheckoutService checkout) {
        this.sales = sales;
        this.reservations = reservations;
        this.checkout = checkout;
    }

    @GetMapping("/active")
    public ActiveSalesResponse active() {
        return sales.active();
    }

    @GetMapping("/{flashSaleId}/stock")
    public StockResponse stock(@PathVariable long flashSaleId) {
        return sales.stock(flashSaleId);
    }

    /** 201 khi giữ suất mới, 200 khi trả lại suất đang chờ của chính người dùng. */
    @PostMapping("/{flashSaleId}/items/{itemId}/reservations")
    public ResponseEntity<ReservationResponse> reserve(@PathVariable long flashSaleId, @PathVariable long itemId,
            @Valid @RequestBody(required = false) ReserveRequest request) {
        int quantity = request == null || request.quantity() == null ? 1 : request.quantity();
        Reserved reserved = reservations.reserve(flashSaleId, itemId, quantity);
        return ResponseEntity.status(reserved.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(reserved.reservation());
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationResponse reservation(@PathVariable String reservationId) {
        return reservations.get(reservationId);
    }

    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<Void> cancel(@PathVariable String reservationId) {
        reservations.cancel(reservationId);
        return ResponseEntity.noContent().build();
    }

    /** 201 khi tạo đơn, 200 khi gửi lại cùng reservation và đơn đã có. */
    @PostMapping("/reservations/{reservationId}/checkout")
    public ResponseEntity<FlashOrderResponse> checkout(@PathVariable String reservationId,
            @RequestBody(required = false) FlashCheckoutRequest request) {
        Placed placed = checkout.checkout(reservationId, request);
        return ResponseEntity.status(placed.created() ? HttpStatus.CREATED : HttpStatus.OK).body(placed.order());
    }
}
