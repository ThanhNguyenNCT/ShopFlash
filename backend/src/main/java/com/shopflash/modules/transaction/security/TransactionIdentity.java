package com.shopflash.modules.transaction.security;

import jakarta.servlet.http.HttpServletRequest;

/** Điểm nối của Transaction với principal F0 đã được Spring Security xác thực. */
public interface TransactionIdentity {
    TransactionCustomer requireCustomer(HttpServletRequest request);
}
