package com.shopflash.modules.transaction.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class OrderCodeGenerator {
    public String next() {
        return "SF-" + UUID.randomUUID().toString().replace("-", "");
    }
}
