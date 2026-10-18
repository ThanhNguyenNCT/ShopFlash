package com.shopflash.common.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisConnectionException;

/** Format lỗi chung và /api/health, không cần DB (MockMvc standalone + mock). */
class CommonWebCoreTest {

    record PriceBody(@Min(0) int price) {
    }

    @RestController
    static class SampleController {
        @GetMapping("/api/sample/json")
        java.util.Map<String, Object> json() {
            return java.util.Map.of("ok", true);
        }

        @GetMapping("/api/sample/page")
        String page(@RequestParam @Min(1) int page) {
            return "ok";
        }

        @GetMapping("/api/sample/missing")
        String missing() {
            throw ApiException.notFound("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm");
        }

        @GetMapping("/api/sample/oracle")
        String oracle() {
            throw new DataAccessResourceFailureException("ORA-12541: TNS:no listener at host 10.0.0.5");
        }

        @GetMapping("/api/sample/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/api/sample/price")
        String price(@Valid @RequestBody PriceBody body) {
            return "ok";
        }
    }

    private final MockMvc sample = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator())   // như app thật (starter-validation): kiểm tra cả @Min trên @RequestParam
            .build();

    private static LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    @Test
    void apiExceptionUsesCommonFormat() throws Exception {
        sample.perform(get("/api/sample/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Không tìm thấy sản phẩm"));
    }

    @Test
    void oracleErrorDoesNotLeakDriverText() throws Exception {
        sample.perform(get("/api/sample/oracle"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("ORACLE_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ORA-"))));
    }

    @Test
    void unexpectedErrorIsGeneric() throws Exception {
        sample.perform(get("/api/sample/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void beanValidationBecomesValidationError() throws Exception {
        sample.perform(post("/api/sample/price").contentType(MediaType.APPLICATION_JSON).content("{\"price\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.price").exists());
    }

    @Test
    void malformedJsonIsValidationError() throws Exception {
        sample.perform(post("/api/sample/price").contentType(MediaType.APPLICATION_JSON).content("{price"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("body"));
    }

    @Test
    void wrongContentTypeIs415NotServerError() throws Exception {
        sample.perform(post("/api/sample/price").contentType(MediaType.TEXT_PLAIN).content("price=1"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void notAcceptableIs406NotServerError() throws Exception {
        sample.perform(get("/api/sample/json").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void requestParamConstraintIsValidationError() throws Exception {
        sample.perform(get("/api/sample/page").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void healthUpWhenBothDatabasesAnswer() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisClient redis = mock(RedisClient.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(redis.ping()).thenReturn("PONG");

        health(jdbc, redis).perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.oracle.status").value("UP"))
                .andExpect(jsonPath("$.redis.status").value("UP"));
    }

    @Test
    void healthDegradedWhenRedisDown() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RedisClient redis = mock(RedisClient.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(redis.ping()).thenThrow(new JedisConnectionException("Connection refused 127.0.0.1:6379"));

        health(jdbc, redis).perform(get("/api/health"))
                .andExpect(status().is(HttpStatus.SERVICE_UNAVAILABLE.value()))
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.redis.status").value("DOWN"))
                .andExpect(jsonPath("$.redis.error").value("JedisConnectionException"));
    }

    private static MockMvc health(JdbcTemplate jdbc, RedisClient redis) {
        return MockMvcBuilders.standaloneSetup(new HealthController(jdbc, redis))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
