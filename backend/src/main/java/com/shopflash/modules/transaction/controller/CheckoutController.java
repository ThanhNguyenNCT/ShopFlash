package com.shopflash.modules.transaction.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.transaction.dto.CheckoutPreviewResponse;
import com.shopflash.modules.transaction.repository.CheckoutRepository;
import com.shopflash.modules.transaction.security.TransactionIdentity;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {
    private final TransactionIdentity identity;
    private final CheckoutRepository checkout;

    public CheckoutController(TransactionIdentity identity, CheckoutRepository checkout) {
        this.identity = identity;
        this.checkout = checkout;
    }

    @GetMapping("/preview")
    public CheckoutPreviewResponse preview(HttpServletRequest request) {
        return checkout.preview(identity.requireCustomer(request).userId());
    }
}
