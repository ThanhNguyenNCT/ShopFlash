package com.shopflash.modules.transaction.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.transaction.dto.CartAddRequest;
import com.shopflash.modules.transaction.dto.CartQuantityRequest;
import com.shopflash.modules.transaction.dto.CartResponse;
import com.shopflash.modules.transaction.repository.CartRepository;
import com.shopflash.modules.transaction.security.TransactionIdentity;
import com.shopflash.modules.transaction.service.CartService;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final TransactionIdentity identity;
    private final CartRepository carts;
    private final CartService service;

    public CartController(TransactionIdentity identity, CartRepository carts, CartService service) {
        this.identity = identity;
        this.carts = carts;
        this.service = service;
    }

    @GetMapping
    public CartResponse currentCart(HttpServletRequest request) {
        return carts.read(identity.requireCustomer(request).userId());
    }

    @PostMapping("/items")
    public CartResponse addItem(HttpServletRequest request, @RequestHeader("X-Cart-Version") long version,
                                @Valid @RequestBody CartAddRequest body) {
        return service.add(identity.requireCustomer(request).userId(), version, body.productId(), body.quantity());
    }

    @PatchMapping("/items/{productId}")
    public CartResponse setQuantity(HttpServletRequest request, @RequestHeader("X-Cart-Version") long version,
                                    @PathVariable long productId, @Valid @RequestBody CartQuantityRequest body) {
        return service.setQuantity(identity.requireCustomer(request).userId(), version, productId, body.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(HttpServletRequest request, @RequestHeader("X-Cart-Version") long version,
                                   @PathVariable long productId) {
        return service.remove(identity.requireCustomer(request).userId(), version, productId);
    }

    @DeleteMapping("/items")
    public CartResponse clear(HttpServletRequest request, @RequestHeader("X-Cart-Version") long version) {
        return service.clear(identity.requireCustomer(request).userId(), version);
    }
}
