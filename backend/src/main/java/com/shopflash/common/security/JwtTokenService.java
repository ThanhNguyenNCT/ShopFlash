package com.shopflash.common.security;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final Duration ttl;

    public JwtTokenService(JwtEncoder encoder,
                           @Value("${shopflash.auth.jwt-issuer:https://shopflash.local}") String issuer,
                           @Value("${shopflash.auth.jwt-audience:shopflash-api}") String audience,
                           @Value("${shopflash.auth.jwt-ttl:PT30M}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalStateException("Thời hạn JWT phải lớn hơn 0 và không quá 30 phút");
        }
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    public IssuedToken issue(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("userId phải dương");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(Long.toString(userId))
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String accessToken, Instant expiresAt) {}
}
