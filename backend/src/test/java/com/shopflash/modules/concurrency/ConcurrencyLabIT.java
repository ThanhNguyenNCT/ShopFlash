package com.shopflash.modules.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.shopflash.common.security.AuthService;
import com.shopflash.common.security.LoginRequest;
import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.dto.request.LabRunRequest;
import com.shopflash.modules.concurrency.dto.response.LabRunResponse;
import com.shopflash.modules.concurrency.dto.response.LabRunResponse.StrategyResult;
import com.shopflash.modules.concurrency.lab.LabStats.RunMetrics;
import com.shopflash.modules.concurrency.lab.LabStrategy;
import com.shopflash.modules.concurrency.service.ConcurrencyLabService;

import jakarta.servlet.Filter;

/**
 * Concurrency Lab trên Oracle và Redis thật: ./mvnw test -Dtest='ConcurrencyLabIT'
 * Cần dữ liệu seed của profile dev (admin@shopflash.local, cc001@shopflash.local).
 * Chỉ ghi cc_lab_items và key cclab:item; xong thì đặt lại về quota mặc định.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConcurrencyLabIT {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("cclab-test-secret-32-byte-random-key!!".getBytes());
    private static final int BUYERS = 300;
    private static final int QUOTA = 50;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired ConcurrencyLabService lab;
    @Autowired AuthService auth;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;

    private LabRunResponse correct;
    private MockMvc mvc;

    @BeforeAll
    void runCorrectStrategiesOnce() {
        EnumSet<LabStrategy> strategies = EnumSet.allOf(LabStrategy.class);
        strategies.remove(LabStrategy.ORACLE_NAIVE);
        strategies.remove(LabStrategy.REDIS_NAIVE);
        correct = lab.run(new LabRunRequest(List.copyOf(strategies), BUYERS, QUOTA, 32, 1, 0));
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
    }

    @AfterAll
    void resetToDefault() {
        lab.reset(100);
    }

    @Test
    void correctStrategiesNeverOversellOrLoseUpdates() {
        assertThat(correct.results()).hasSize(8);
        for (StrategyResult result : correct.results()) {
            RunMetrics m = result.runs().get(0);
            assertThat(m.oversold()).as(result.strategy() + " oversold").isZero();
            assertThat(m.lostUpdates()).as(result.strategy() + " lostUpdates").isZero();
            assertThat(m.finalSold()).as(result.strategy() + " finalSold").isEqualTo(m.successes());
            assertThat(m.successes() + m.soldOut() + m.errors()).as(result.strategy() + " lượt").isEqualTo(BUYERS);
        }
    }

    @Test
    void blockingAndSingleStepStrategiesSellExactlyTheQuota() {
        for (StrategyResult result : correct.results()) {
            if (EnumSet.of(LabStrategy.ORACLE_ATOMIC, LabStrategy.ORACLE_PESSIMISTIC, LabStrategy.REDIS_LUA,
                    LabStrategy.REDIS_DECR).contains(result.strategy())) {
                RunMetrics m = result.runs().get(0);
                assertThat(m.successes()).as(result.strategy().name()).isEqualTo(QUOTA);
                assertThat(m.soldOut()).as(result.strategy().name()).isEqualTo(BUYERS - QUOTA);
                assertThat(m.errors()).as(result.strategy().name()).isZero();
            }
        }
    }

    @Test
    void optimisticStrategiesCountTheirRetries() {
        StrategyResult watch = find(correct, LabStrategy.REDIS_WATCH);
        StrategyResult optimistic = find(correct, LabStrategy.ORACLE_OPTIMISTIC);
        // Có tranh chấp (32 lượt cùng lúc) nên ít nhất phải có lần thử lại.
        assertThat(watch.runs().get(0).retries()).isPositive();
        assertThat(optimistic.runs().get(0).retries()).isPositive();
    }

    @Test
    void naiveStrategiesKeepTheCounterValidButMayLoseUpdates() {
        LabRunResponse naive = lab.run(new LabRunRequest(
                List.of(LabStrategy.ORACLE_NAIVE, LabStrategy.REDIS_NAIVE), BUYERS, QUOTA, 32, 1, 0));
        for (StrategyResult result : naive.results()) {
            RunMetrics m = result.runs().get(0);
            // Bộ đếm luôn hợp lệ (CHECK sold <= allocated không bị vi phạm) nên lỗi chỉ thấy khi đếm người mua.
            assertThat(m.finalSold()).as(result.strategy().name()).isBetween(1L, (long) QUOTA);
            assertThat(m.lostUpdates()).isEqualTo(m.successes() - m.finalSold());
            assertThat(m.oversold()).isEqualTo(Math.max(0, m.successes() - QUOTA));
            System.out.printf("%s: successes=%d finalSold=%d oversold=%d lostUpdates=%d%n", result.strategy(),
                    m.successes(), m.finalSold(), m.oversold(), m.lostUpdates());
        }
    }

    @Test
    void secondRunWhileRunningIsRejected() throws Exception {
        CompletableFuture<LabRunResponse> longRun = CompletableFuture.supplyAsync(() -> lab.run(
                new LabRunRequest(List.of(LabStrategy.ORACLE_PESSIMISTIC), 3000, 3000, 8, 1, 0)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!lab.state().running() && !longRun.isDone() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(lab.state().running()).as("lần chạy dài phải đang chạy").isTrue();

        assertThatThrownBy(() -> lab.reset(5)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo("LAB_RUN_IN_PROGRESS"));

        assertThat(longRun.get(60, TimeUnit.SECONDS).results().get(0).runs().get(0).successes()).isEqualTo(3000);
        assertThat(lab.state().running()).isFalse();
    }

    @Test
    void resetSetsBothCounters() throws Exception {
        String admin = login("admin@shopflash.local");
        mvc.perform(post("/api/admin/concurrency-lab/reset").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quota\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oracle.allocated").value(7))
                .andExpect(jsonPath("$.oracle.sold").value(0))
                .andExpect(jsonPath("$.redis.allocated").value(7))
                .andExpect(jsonPath("$.redis.remaining").value(7))
                .andExpect(jsonPath("$.running").value(false));
        mvc.perform(get("/api/admin/concurrency-lab/state").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oracle.allocated").value(7));
    }

    @Test
    void apiValidatesInputAndRequiresAdmin() throws Exception {
        String admin = login("admin@shopflash.local");
        mvc.perform(post("/api/admin/concurrency-lab/runs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"buyers\": 10, \"quota\": 20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.field").value("quota"));
        mvc.perform(post("/api/admin/concurrency-lab/runs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"concurrency\": 999}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/concurrency-lab/runs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"strategies\": [\"NOPE\"]}"))
                .andExpect(status().isBadRequest());

        String customer = login("cc001@shopflash.local");
        mvc.perform(get("/api/admin/concurrency-lab/state").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/concurrency-lab/state")).andExpect(status().isUnauthorized());
    }

    @Test
    void runEndpointReturnsConfigAndSummary() throws Exception {
        String admin = login("admin@shopflash.local");
        mvc.perform(post("/api/admin/concurrency-lab/runs").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"strategies\": [\"REDIS_LUA\"], \"buyers\": 50, \"quota\": 10, \"concurrency\": 8,"
                        + " \"runs\": 2, \"warmup\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.buyers").value(50))
                .andExpect(jsonPath("$.config.redisPoolSize").value(16))
                .andExpect(jsonPath("$.results[0].strategy").value("REDIS_LUA"))
                .andExpect(jsonPath("$.results[0].store").value("REDIS"))
                .andExpect(jsonPath("$.results[0].runs.length()").value(2))
                .andExpect(jsonPath("$.results[0].summary.successes.mean").value(10.0))
                .andExpect(jsonPath("$.results[0].summary.successes.sd").value(0.0));
    }

    private StrategyResult find(LabRunResponse response, LabStrategy strategy) {
        return response.results().stream().filter(r -> r.strategy() == strategy).findFirst().orElseThrow();
    }

    private String login(String email) {
        return auth.login(new LoginRequest(email, "ShopFlash123!")).accessToken();
    }
}
