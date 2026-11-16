package com.shopflash.modules.query.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.PageInfo;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.VipCustomerRow;
import com.shopflash.modules.query.dto.response.VipSummary;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.cache.CachedReportSource;
import com.shopflash.modules.query.repository.cache.ReportCacheStore;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachedReportSourceCoreTest {

    /** Kho cache trong bộ nhớ; ghi lại TTL; failing=true giả lập Redis không kết nối được. */
    static class MemoryStore implements ReportCacheStore {
        final Map<String, String> values = new HashMap<>();
        final Map<String, Duration> ttls = new HashMap<>();
        boolean failing;

        @Override
        public Optional<String> get(String key) {
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            if (failing) {
                throw new IllegalStateException("redis down");
            }
            values.put(key, value);
            ttls.put(key, ttl);
        }
    }

    static final Duration TTL = Duration.ofMinutes(5);
    static final ReportQuery MONTH = new ReportQuery(Report.REVENUE_BY_MONTH, LocalDate.of(2025, 12, 1),
            LocalDate.of(2026, 11, 11), JoinHint.AUTO, RedisMode.AGGREGATE, 10, 1, 20);

    FakeReportSource oracle;
    MemoryStore store;
    CachedReportSource cached;

    @BeforeEach
    void setUp() {
        oracle = new FakeReportSource(Source.ORACLE, q -> SourceResult.of(
                ReportData.of(List.of(new MonthRevenueRow("2026-10", 812, new BigDecimal("912345000.50")))),
                q.joinHint().name(), 1, "SELECT ..."));
        store = new MemoryStore();
        cached = new CachedReportSource(oracle, store, TTL, ReportServiceCoreTest.CLOCK);
    }

    @Test
    void missRunsOracleAndStoresWithTtl() {
        SourceResult r = cached.run(MONTH);

        assertThat(cached.source()).isEqualTo(Source.CACHE);
        assertThat(r.variant()).isEqualTo("MISS");
        assertThat(r.roundTrips()).isEqualTo(3); // GET + câu SQL + SET
        assertThat(r.statement()).startsWith("GET report:cache:").contains("SELECT ...").contains("SET ");
        assertThat(oracle.runs).hasSize(1);
        assertThat(store.values).hasSize(1);
        assertThat(store.ttls.values()).containsOnly(TTL);
        assertThat(List.<Object>copyOf(r.data().rows())).containsExactly(new MonthRevenueRow("2026-10", 812, new BigDecimal("912345000.50")));
    }

    @Test
    void hitDoesNotTouchOracleAndKeepsExactValues() {
        cached.run(MONTH);
        SourceResult r = cached.run(MONTH);

        assertThat(r.variant()).isEqualTo("HIT");
        assertThat(r.roundTrips()).isEqualTo(1);
        assertThat(r.statement()).startsWith("GET report:cache:revenue-by-month:");
        assertThat(oracle.runs).hasSize(1);
        // BigDecimal giữ nguyên scale, để /compare và báo cáo không đổi số.
        assertThat(List.<Object>copyOf(r.data().rows())).containsExactly(new MonthRevenueRow("2026-10", 812, new BigDecimal("912345000.50")));
        assertThat(r.note()).contains("lưu lúc");
    }

    @Test
    void differentParamsUseDifferentKeys() {
        cached.run(MONTH);
        cached.run(new ReportQuery(Report.REVENUE_BY_MONTH, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 11),
                JoinHint.AUTO, RedisMode.AGGREGATE, 10, 1, 20));
        cached.run(MONTH.withJoinHint(JoinHint.NL));

        assertThat(store.values).hasSize(3);
        assertThat(oracle.runs).hasSize(3);
    }

    @Test
    void pagedReportKeepsPageAndSummary() {
        ReportQuery vip = new ReportQuery(Report.VIP_CUSTOMERS, MONTH.from(), MONTH.to(), JoinHint.AUTO,
                RedisMode.AGGREGATE, 10, 2, 20);
        oracle.behavior = q -> SourceResult.of(new ReportData(
                List.of(new VipCustomerRow(52788, "Vu Thi Giang", "c@x", 35, new BigDecimal("899920000.00"))),
                new PageInfo(2, 20, 5960), new VipSummary(new BigDecimal("67831353.25"), 18972)), "AUTO", 2, "SELECT ...");

        cached.run(vip);
        ReportData hit = cached.run(vip).data();

        assertThat(hit.page()).isEqualTo(new PageInfo(2, 20, 5960));
        assertThat(hit.summary()).isEqualTo(new VipSummary(new BigDecimal("67831353.25"), 18972));
        assertThat(hit.rows()).hasSize(1).first().isInstanceOf(VipCustomerRow.class);
    }

    @Test
    void redisDownFallsBackToOracle() {
        store.failing = true;

        SourceResult r = cached.run(MONTH);

        assertThat(r.variant()).isEqualTo("MISS");
        assertThat(r.data().rows()).hasSize(1);
        assertThat(r.note()).contains("Redis");
        assertThat(oracle.runs).hasSize(1);
    }

    @Test
    void planIsOraclePlan() {
        assertThat(cached.plan(MONTH).source()).isEqualTo("oracle");
        assertThat(oracle.plans).hasSize(1);
    }
}
