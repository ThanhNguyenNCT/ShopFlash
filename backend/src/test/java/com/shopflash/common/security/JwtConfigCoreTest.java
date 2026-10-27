package com.shopflash.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtConfigCoreTest {
    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key, "https://shopflash.local", "shopflash-api");

    @Test
    void rejectsMissingWeakAndMalformedSecret() {
        assertThatThrownBy(() -> config.jwtSigningKey("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.jwtSigningKey(Base64.getEncoder().encodeToString(new byte[31])))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.jwtSigningKey("not-base64!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenService(encoder, "https://shopflash.local", "shopflash-api", Duration.ofMinutes(31)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issuesAndChecksSignedJwtWithExpectedClaims() {
        JwtTokenService.IssuedToken issued = new JwtTokenService(
                encoder, "https://shopflash.local", "shopflash-api", Duration.ofMinutes(30)).issue(42);
        var jwt = decoder.decode(issued.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://shopflash.local");
        assertThat(jwt.getAudience()).containsExactly("shopflash-api");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getExpiresAt()).isEqualTo(issued.expiresAt());
        byte[] differentKey = new byte[32];
        differentKey[0] = 1;
        assertThatThrownBy(() -> config.jwtDecoder(config.jwtSigningKey(
                Base64.getEncoder().encodeToString(differentKey)), "https://shopflash.local", "shopflash-api")
                .decode(issued.accessToken())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsWrongIssuerAudienceAndExpiredToken() {
        assertThatThrownBy(() -> decoder.decode(sign("other", "shopflash-api", Instant.now().plusSeconds(60))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> decoder.decode(sign("https://shopflash.local", "other", Instant.now().plusSeconds(60))))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> decoder.decode(sign("https://shopflash.local", "shopflash-api", Instant.now().minusSeconds(60))))
                .isInstanceOf(RuntimeException.class);
    }

    private String sign(String issuer, String audience, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject("42").issuer(issuer)
                .audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(120))
                .expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
