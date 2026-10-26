package com.shopflash.modules.transaction.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.transaction.dto.CheckoutRequest;
import com.shopflash.modules.transaction.dto.PlacedOrderResponse;
import com.shopflash.modules.transaction.security.TransactionIdentity;
import com.shopflash.modules.transaction.service.CheckoutService;

@RestController
public class OrdersCheckoutController {
    private final TransactionIdentity identity;
    private final CheckoutService checkout;

    public OrdersCheckoutController(TransactionIdentity identity, CheckoutService checkout) {
        this.identity = identity;
        this.checkout = checkout;
    }

    @PostMapping("/api/orders")
    public PlacedOrderResponse place(HttpServletRequest request, @RequestHeader("Idempotency-Key") String key,
                                     @RequestBody CheckoutRequest body) {
        return checkout.place(identity.requireCustomer(request).userId(), key, body);
    }
}
