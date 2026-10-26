package com.shopflash.modules.transaction.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.shopflash.common.security.AuthPrincipal;
import com.shopflash.common.web.ApiException;

/** Dùng principal đã được Spring Security kiểm tra chữ ký và tra lại Oracle. */
@Component
public class JwtTransactionIdentity implements TransactionIdentity {
    @Override
    public TransactionCustomer requireCustomer(HttpServletRequest request) {
        if (request.getHeader("X-User-Id") != null || request.getParameter("userId") != null) {
            throw ApiException.badRequest("userId", "Không được tự khai danh tính khách hàng");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Cần đăng nhập bằng tài khoản khách hàng");
        }
        if (!"CUSTOMER".equals(principal.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_REQUIRED", "Tài khoản không có quyền khách hàng");
        }
        return new TransactionCustomer(principal.userId(), principal.email(), principal.role());
    }
}
