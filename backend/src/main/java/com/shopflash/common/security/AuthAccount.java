package com.shopflash.common.security;

public record AuthAccount(long userId, String email, String passwordHash, String fullName,
                          String phone, String role, String status) {
    public AuthPrincipal principal() {
        return new AuthPrincipal(userId, email, fullName, role);
    }
}
