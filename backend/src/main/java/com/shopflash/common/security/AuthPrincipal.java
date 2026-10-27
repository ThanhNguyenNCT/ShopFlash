package com.shopflash.common.security;

public record AuthPrincipal(long userId, String email, String fullName, String role) {}
