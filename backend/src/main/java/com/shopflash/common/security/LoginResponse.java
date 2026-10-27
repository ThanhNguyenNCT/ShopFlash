package com.shopflash.common.security;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, AuthPrincipal user) {}
