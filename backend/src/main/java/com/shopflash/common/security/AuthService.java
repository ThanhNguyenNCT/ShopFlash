package com.shopflash.common.security;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopflash.common.web.ApiException;

@Service
public class AuthService {
    private final AuthAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;

    public AuthService(AuthAccountRepository accounts, PasswordEncoder passwords, JwtTokenService tokens) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public AuthPrincipal register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String name = request.fullName().strip();
        if (name.isEmpty()) throw ApiException.badRequest("fullName", "Họ tên không được để trống");
        if (email.isEmpty() || email.length() > 255) throw ApiException.badRequest("email", "Email không hợp lệ");
        String phone = request.phone() == null ? null : request.phone().strip();
        if (phone != null && phone.isEmpty()) phone = null;
        if (request.password().length() < 10 || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("password", "Mật khẩu cần ít nhất 10 ký tự và tối đa 72 byte UTF-8");
        }
        if (accounts.findByEmail(email).isPresent()) throw emailConflict();
        try {
            accounts.insertCustomer(email, passwords.encode(request.password()), name, phone);
        } catch (DataIntegrityViolationException e) {
            if (!hasOracleCode(e, 1)) throw e;
            throw emailConflict();
        }
        return accounts.findByEmail(email).orElseThrow().principal();
    }

    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        AuthAccount account = accounts.findByEmail(email).orElse(null);
        if (account == null || !"ACTIVE".equals(account.status())
                || !passwords.matches(request.password(), account.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
        }
        JwtTokenService.IssuedToken issued = tokens.issue(account.userId());
        return new LoginResponse(issued.accessToken(), "Bearer", issued.expiresAt(), account.principal());
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailConflict() {
        return ApiException.conflict("EMAIL_EXISTS", "Email đã được đăng ký");
    }

    private static boolean hasOracleCode(Throwable error, int code) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == code) return true;
        }
        return false;
    }
}
