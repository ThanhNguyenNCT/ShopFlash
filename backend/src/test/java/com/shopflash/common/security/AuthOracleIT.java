package com.shopflash.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Chạy trên schema Oracle dùng riêng; các request HTTP và truy vấn kiểm tra dùng kết nối độc lập. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthOracleIT {
    private static final String SECRET = Base64.getEncoder().encodeToString("txauth-test-secret-32-byte-random-key!".getBytes());
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @LocalServerPort int port;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired ObjectMapper json;
    private final Set<String> fixtureEmails = new HashSet<>();

    @AfterEach
    void cleanUp() {
        fixtureEmails.forEach(email -> jdbc.update("DELETE FROM users WHERE email = :email", Map.of("email", email)));
        fixtureEmails.clear();
    }

    @Test
    void registerLoginAndMeUseOracleAccountWithoutExposingHash() throws Exception {
        String email = email();
        String password = "Mật khẩu khá dài 123";
        HttpResponse<String> registered = send("POST", "/api/auth/register", """
                {"fullName":" Khách thử ","email":"  %s  ","phone":" 0901234567 ","password":"%s"}
                """.formatted(email.toUpperCase(), password), null);
        assertThat(registered.statusCode()).isEqualTo(201);
        JsonNode user = json.readTree(registered.body());
        assertThat(user.get("email").asText()).isEqualTo(email);
        assertThat(user.get("fullName").asText()).isEqualTo("Khách thử");
        assertThat(user.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(registered.body()).doesNotContain("password", "hash", password);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT password_hash, role, status, phone FROM users WHERE email = :email", Map.of("email", email));
        assertThat(passwordEncoder.matches(password, (String) row.get("PASSWORD_HASH"))).isTrue();
        assertThat(row.get("ROLE")).isEqualTo("CUSTOMER");
        assertThat(row.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(row.get("PHONE")).isEqualTo("0901234567");

        HttpResponse<String> loggedIn = send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), null);
        assertThat(loggedIn.statusCode()).isEqualTo(200);
        JsonNode login = json.readTree(loggedIn.body());
        assertThat(login.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(login.get("expiresAt").asText()).isNotBlank();
        assertThat(login.get("user").get("userId").asLong()).isEqualTo(user.get("userId").asLong());
        assertThat(loggedIn.body()).doesNotContain("password", "hash", password);
        String token = login.get("accessToken").asText();
        HttpResponse<String> me = send("GET", "/api/auth/me", null, token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).get("email").asText()).isEqualTo(email);

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE email = :email", Map.of("email", email));
        assertThat(send("GET", "/api/auth/me", null, token).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password), null).statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsDuplicateInvalidAndSelfDeclaredIdentity() throws Exception {
        String email = email();
        String valid = "{\"fullName\":\"Test\",\"email\":\"%s\",\"password\":\"1234567890\"}".formatted(email);
        assertThat(send("POST", "/api/auth/register", valid, null).statusCode()).isEqualTo(201);
        HttpResponse<String> duplicate = send("POST", "/api/auth/register",
                valid.replace(email, email.toUpperCase()), null);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json.readTree(duplicate.body()).get("error").get("code").asText()).isEqualTo("EMAIL_EXISTS");
        assertThat(send("POST", "/api/auth/register",
                valid.replace("1234567890", "short"), null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/auth/register",
                valid.replace(email, "not-an-email"), null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/auth/register",
                valid.replace("\"password\"", "\"role\":\"ADMIN\",\"password\""), null).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/auth/register",
                valid.replace("\"password\"", "\"userId\":1,\"password\""), null).statusCode()).isEqualTo(400);
        HttpResponse<String> badPassword = send("POST", "/api/auth/login",
                "{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email), null);
        HttpResponse<String> unknownEmail = send("POST", "/api/auth/login",
                "{\"email\":\"missing-%s@example.test\",\"password\":\"wrong-password\"}".formatted(UUID.randomUUID()), null);
        assertThat(badPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(badPassword.body()).isEqualTo(unknownEmail.body());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = :email",
                Map.of("email", email), Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationsCreateOneAccount() throws Exception {
        String email = email();
        String body = "{\"fullName\":\"Test\",\"email\":\"%s\",\"password\":\"1234567890\"}".formatted(email);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> { start.await(); return send("POST", "/api/auth/register", body, null).statusCode(); });
            var second = workers.submit(() -> { start.await(); return send("POST", "/api/auth/register", body, null).statusCode(); });
            start.countDown();
            assertThat(new int[]{first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = :email",
                Map.of("email", email), Integer.class)).isEqualTo(1);
    }

    @Test
    void securityChainChecksTokenAndCurrentOracleRole() throws Exception {
        String customerEmail = email();
        String adminEmail = email();
        String customerToken = registerAndLogin(customerEmail);
        String adminToken = registerAndLogin(adminEmail);

        assertThat(send("GET", "/api/cart", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/cart", null, "not-a-jwt").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/admin/categories", null, customerToken).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/auth/me", null, customerToken).statusCode()).isEqualTo(200);

        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = :email", Map.of("email", adminEmail));
        assertThat(send("GET", "/api/admin/categories", null, adminToken).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/cart", null, adminToken).statusCode()).isEqualTo(403);

        long customerId = jdbc.queryForObject("SELECT user_id FROM users WHERE email = :email",
                Map.of("email", customerEmail), Long.class);
        Instant now = Instant.now();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(Long.toString(customerId))
                        .issuer("https://shopflash.local").audience(List.of("shopflash-api"))
                        .issuedAt(now.minus(Duration.ofHours(2)))
                        .expiresAt(now.minus(Duration.ofHours(1))).build())).getTokenValue();
        assertThat(send("GET", "/api/cart", null, expired).statusCode()).isEqualTo(401);

        jdbc.update("UPDATE users SET status = 'DISABLED' WHERE email = :email", Map.of("email", customerEmail));
        assertThat(send("GET", "/api/auth/me", null, customerToken).statusCode()).isEqualTo(401);
    }

    private String registerAndLogin(String email) throws Exception {
        String body = "{\"fullName\":\"Test\",\"email\":\"%s\",\"password\":\"1234567890\"}".formatted(email);
        assertThat(send("POST", "/api/auth/register", body, null).statusCode()).isEqualTo(201);
        String login = "{\"email\":\"%s\",\"password\":\"1234567890\"}".formatted(email);
        HttpResponse<String> response = send("POST", "/api/auth/login", login, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("accessToken").asText();
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String email() {
        String value = "txauth-" + UUID.randomUUID() + "@example.test";
        fixtureEmails.add(value);
        return value;
    }
}
