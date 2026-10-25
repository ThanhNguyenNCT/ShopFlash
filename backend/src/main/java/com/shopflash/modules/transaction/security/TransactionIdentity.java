package com.shopflash.modules.transaction.security;

import jakarta.servlet.http.HttpServletRequest;

/** Điểm nối với F0 khi backend có xác thực thật. */
public interface TransactionIdentity {
    TransactionCustomer requireCustomer(HttpServletRequest request);
}
