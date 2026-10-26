package com.shopflash.common.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtAccountConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthAccountRepository accounts;

    public JwtAccountConverter(AuthAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BadCredentialsException("JWT không có tài khoản hợp lệ", e);
        }
        AuthAccount account = accounts.findById(userId)
                .filter(candidate -> "ACTIVE".equals(candidate.status()))
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không còn hoạt động"));
        return UsernamePasswordAuthenticationToken.authenticated(account.principal(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
    }
}
