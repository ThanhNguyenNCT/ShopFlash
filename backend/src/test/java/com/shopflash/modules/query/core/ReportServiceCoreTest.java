package com.shopflash.modules.query.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.ReportResponse;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.service.ReportService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportServiceCoreTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-18T03:00:00.123Z"), ZoneOffset.UTC);

    static ReportQuery query(Report report) {
        return new ReportQuery(report, LocalDate.of(2025, 11, 1), LocalDate.of(2026, 10, 18),
                JoinHint.AUTO, RedisMode.AGGREGATE, 10, 1, 20);
    }

    static SourceResult months(String variant) {
        return SourceResult.of(ReportData.of(List.of(
                new MonthRevenueRow("2026-09", 3, new BigDecimal("100.00")),
                new MonthRevenueRow("2026-10", 4, new BigDecimal("200.00")))), variant, 1, "SELECT 1");
    }

    @Test
    void routesToSourceAndBuildsMeta() {
        FakeReportSource oracle = new FakeReportSource(Source.ORACLE, q -> months("AUTO"));
        FakeReportSource redis = new FakeReportSource(Source.REDIS, q -> months("AGGREGATE"));
        ReportService service = new ReportService(List.of(oracle, redis), CLOCK);

        ReportResponse res = service.run(query(Report.REVENUE_BY_MONTH), Source.REDIS);

        assertThat(redis.runs).hasSize(1);
        assertThat(oracle.runs).isEmpty();
        assertThat(res.meta().source()).isEqualTo("redis");
        assertThat(res.meta().report()).isEqualTo("revenue-by-month");
        assertThat(res.meta().variant()).isEqualTo("AGGREGATE");
        assertThat(res.meta().supported()).isTrue();
        assertThat(res.meta().rowCount()).isEqualTo(2);
        assertThat(res.meta().roundTrips()).isEqualTo(1);
        assertThat(res.meta().statement()).isEqualTo("SELECT 1");
        assertThat(res.meta().note()).isNull();
        assertThat(res.meta().generatedAt()).isEqualTo(OffsetDateTime.parse("2026-10-18T10:00:00+07:00"));
        assertThat(res.meta().generatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(7));
        assertThat(res.meta().elapsedMs()).isGreaterThanOrEqualTo(0);
        assertThat(res.meta().elapsedMs() * 100).isEqualTo(Math.rint(res.meta().elapsedMs() * 100));
        assertThat(res.data().rows()).hasSize(2);
    }

    @Test
    void measuresElapsedAroundRun() {
        FakeReportSource oracle = new FakeReportSource(Source.ORACLE, q -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return months("AUTO");
        });
        ReportService service = new ReportService(List.of(oracle), CLOCK);
        assertThat(service.run(query(Report.REVENUE_BY_MONTH), Source.ORACLE).meta().elapsedMs())
                .isGreaterThanOrEqualTo(29.0);
    }

    @Test
    void unsupportedHasNullDataAndNote() {
        FakeReportSource redis = new FakeReportSource(Source.REDIS,
                q -> SourceResult.unsupported("AGGREGATE", "Redis không hỗ trợ NOT EXISTS"));
        ReportService service = new ReportService(List.of(redis), CLOCK);

        ReportResponse res = service.run(query(Report.UNSOLD_PRODUCTS), Source.REDIS);

        assertThat(res.data()).isNull();
        assertThat(res.meta().supported()).isFalse();
        assertThat(res.meta().rowCount()).isZero();
        assertThat(res.meta().note()).isEqualTo("Redis không hỗ trợ NOT EXISTS");
    }

    @Test
    void missingRedisSourceIs503AndMissingOracleIs500() {
        ReportService service = new ReportService(List.of(), CLOCK);
        assertThatThrownBy(() -> service.run(query(Report.TOP_PRODUCTS), Source.REDIS))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE);
        assertThatThrownBy(() -> service.plan(query(Report.TOP_PRODUCTS), Source.ORACLE))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.ORACLE_ERROR);
    }

    @Test
    void planDelegatesToChosenSource() {
        FakeReportSource oracle = new FakeReportSource(Source.ORACLE, q -> months("AUTO"));
        ReportService service = new ReportService(List.of(oracle), CLOCK);
        var plan = service.plan(query(Report.TOP_PRODUCTS).withJoinHint(JoinHint.HASH), Source.ORACLE);
        assertThat(plan.variant()).isEqualTo("HASH");
        assertThat(oracle.plans).hasSize(1);
        assertThat(oracle.runs).isEmpty();
    }

    @Test
    void toMillisRoundsToTwoDecimals() {
        assertThat(ReportService.toMillis(12_844_999)).isEqualTo(12.84);
        assertThat(ReportService.toMillis(12_845_000)).isEqualTo(12.85);
        assertThat(ReportService.toMillis(0)).isEqualTo(0.0);
    }
}
