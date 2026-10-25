package com.shopflash.modules.transaction.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.shopflash.common.web.ApiException;

/** Từ chối mặc định cho tới khi F0 backend cung cấp danh tính đáng tin. */
@Component
@Profile("!tx-local")
public class UnavailableTransactionIdentity implements TransactionIdentity {
    @Override
    public TransactionCustomer requireCustomer(HttpServletRequest request) {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Cần xác thực khách hàng");
    }
}
