package com.shopflash.modules.concurrency.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.shopflash.common.security.AuthPrincipal;
import com.shopflash.common.web.ApiException;

/**
 * Người mua hiện tại, lấy từ JWT mà common/security đã kiểm tra và tra lại Oracle.
 * Không nhận userId từ body, query hay header do trình duyệt tự khai.
 */
@Component
public class CurrentCustomer {

    public long requireCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Cần đăng nhập để mua flash sale");
        }
        if (!"CUSTOMER".equals(principal.role())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_REQUIRED",
                    "Chỉ tài khoản khách hàng được mua flash sale");
        }
        return principal.userId();
    }
}
