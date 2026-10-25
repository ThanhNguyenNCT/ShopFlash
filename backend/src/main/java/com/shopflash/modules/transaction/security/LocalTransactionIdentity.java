package com.shopflash.modules.transaction.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.shopflash.common.web.ApiException;

/** Danh tính dùng riêng cho máy local/test, không phải hệ thống đăng nhập F0. */
@Component
@Profile("tx-local")
public class LocalTransactionIdentity implements TransactionIdentity {
    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+", Pattern.CASE_INSENSITIVE);

    private final Map<String, String> emailByToken;
    private final TransactionCustomerRepository customers;

    public LocalTransactionIdentity(
            @Value("${server.address:127.0.0.1}") String bindAddress,
            @Value("${TX_LOCAL_TOKEN_FILE:}") String tokenFile,
            TransactionCustomerRepository customers) {
        if (!"127.0.0.1".equals(bindAddress)) {
            throw new IllegalStateException("tx-local chỉ được bật khi server.address=127.0.0.1");
        }
        this.emailByToken = readTokenFile(tokenFile);
        this.customers = customers;
    }

    @Override
    public TransactionCustomer requireCustomer(HttpServletRequest request) {
        if (request.getHeader("X-User-Id") != null || request.getParameter("userId") != null) {
            throw ApiException.badRequest("userId", "Không được tự khai danh tính khách hàng");
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw authRequired();
        }
        String token = authorization.substring(7);
        String email = emailByToken.get(token);
        if (email == null) {
            throw authRequired();
        }
        TransactionCustomer customer = customers.findActiveCustomer(email);
        if (customer == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_REQUIRED", "Tài khoản khách hàng không hợp lệ");
        }
        return customer;
    }

    private static Map<String, String> readTokenFile(String tokenFile) {
        if (tokenFile == null || tokenFile.isBlank()) {
            throw new IllegalStateException("Thiếu TX_LOCAL_TOKEN_FILE cho profile tx-local");
        }
        Path path = Path.of(tokenFile);
        if (!path.isAbsolute() || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("TX_LOCAL_TOKEN_FILE phải là tệp đọc được với đường dẫn tuyệt đối");
        }
        try {
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty() || !"token,email".equals(lines.getFirst().strip())) {
                throw new IllegalStateException("TX_LOCAL_TOKEN_FILE phải có tiêu đề token,email");
            }
            Map<String, String> mappings = new HashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                if (line.isEmpty()) continue;
                String[] fields = line.split(",", -1);
                if (fields.length != 2 || !TOKEN.matcher(fields[0]).matches()
                        || !EMAIL.matcher(fields[1]).matches()
                        || !fields[1].equals(fields[1].toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("Dòng token local không hợp lệ tại dòng " + (i + 1));
                }
                if (mappings.putIfAbsent(fields[0], fields[1]) != null) {
                    throw new IllegalStateException("Token local bị trùng tại dòng " + (i + 1));
                }
            }
            if (mappings.isEmpty()) {
                throw new IllegalStateException("TX_LOCAL_TOKEN_FILE chưa có token");
            }
            return Map.copyOf(mappings);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được TX_LOCAL_TOKEN_FILE", e);
        }
    }

    private static ApiException authRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Cần token khách hàng hợp lệ");
    }
}
