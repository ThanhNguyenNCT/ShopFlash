package com.shopflash.modules.transaction.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.transaction.dto.OrderDetailResponse;
import com.shopflash.modules.transaction.dto.OrderListResponse;
import com.shopflash.modules.transaction.security.TransactionIdentity;
import com.shopflash.modules.transaction.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class CustomerOrdersController {
    private final TransactionIdentity identity;
    private final OrderService orders;

    public CustomerOrdersController(TransactionIdentity identity, OrderService orders) {
        this.identity = identity;
        this.orders = orders;
    }

    @GetMapping
    public OrderListResponse list(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String status) {
        return orders.list(identity.requireCustomer(request).userId(), page, size, status);
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse detail(HttpServletRequest request, @PathVariable long orderId) {
        return orders.detail(identity.requireCustomer(request).userId(), orderId);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderDetailResponse cancel(HttpServletRequest request, @PathVariable long orderId) {
        return orders.cancel(identity.requireCustomer(request).userId(), orderId);
    }
}
