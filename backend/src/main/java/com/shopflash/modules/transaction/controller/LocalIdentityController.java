package com.shopflash.modules.transaction.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shopflash.modules.transaction.security.TransactionCustomer;
import com.shopflash.modules.transaction.security.TransactionIdentity;

/** Chỉ có trong profile local/test để giao diện đối chiếu với người dùng giả lập. */
@RestController
@Profile("tx-local")
public class LocalIdentityController {
    private final TransactionIdentity identity;

    public LocalIdentityController(TransactionIdentity identity) {
        this.identity = identity;
    }

    @GetMapping("/api/transaction/local-identity")
    public TransactionCustomer currentCustomer(HttpServletRequest request) {
        return identity.requireCustomer(request);
    }
}
