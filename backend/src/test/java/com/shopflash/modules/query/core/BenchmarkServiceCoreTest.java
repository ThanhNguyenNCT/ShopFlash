package com.shopflash.modules.query.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopflash.modules.query.dto.request.BenchmarkRequest;
import com.shopflash.modules.query.dto.request.BenchmarkRequest.BenchmarkVariant;
import com.shopflash.modules.query.dto.response.BenchmarkResponse;
import com.shopflash.modules.query.dto.response.BenchmarkResult;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.service.BenchmarkService;
import com.shopflash.modules.query.service.BenchmarkStats;
import com.shopflash.modules.query.service.ReportQueryFactory;
import com.shopflash.modules.query.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkServiceCoreTest {

    FakeReportSource oracle = new FakeReportSource(Source.ORACLE,
            q -> SourceResult.of(null, q.joinHint().name(), 1, "SQL"));
    FakeReportSource redis = new FakeReportSource(Source.REDIS,
            q -> q.report() == Report.UNSOLD_PRODUCTS
                    ? SourceResult.unsupported(q.redisMode().name(), "không hỗ trợ")
                    : SourceResult.of(null, q.redisMode().name(), q.redisMode() == RedisMode.CLIENT ? 3 : 1, "FT"));
    BenchmarkService service = new BenchmarkService(
            new ReportService(List.of(oracle, redis), ReportServiceCoreTest.CLOCK),
            new ReportQueryFactory(ReportServiceCoreTest.CLOCK));

    @Test
    void statsOnFixedSamples() {
        BenchmarkStats s = BenchmarkStats.of(List.of(20.1, 21.0, 22.9, 21.0, 23.1));
        assertThat(s.mean()).isEqualTo(21.62);
        assertThat(s.stdDev()).isEqualTo(1.31); // sqrt(6.908 / 4) = 1.3142
        assertThat(s.p50()).isEqualTo(21.0);
        assertThat(s.p95()).isEqualTo(23.1);

        BenchmarkStats ten = BenchmarkStats.of(List.of(10.0, 9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0));
        assertThat(ten.mean()).isEqualTo(5.5);
        assertThat(ten.stdDev()).isEqualTo(3.03); // sqrt(82.5 / 9) = 3.0277
        assertThat(ten.p50()).isEqualTo(5.0);     // rank ceil(5.0) = 5
        assertThat(ten.p95()).isEqualTo(10.0);    // rank ceil(9.5) = 10

        BenchmarkStats twenty = BenchmarkStats.of(java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(i -> (double) i).toList());
        assertThat(twenty.p95()).isEqualTo(19.0); // rank ceil(19.0) = 19
        assertThat(twenty.p50()).isEqualTo(10.0);

        BenchmarkStats one = BenchmarkStats.of(List.of(4.2));
        assertThat(one.stdDev()).isZero();
        assertThat(one.p50()).isEqualTo(4.2);
        assertThat(one.p95()).isEqualTo(4.2);
    }

    @Test
    void defaultsRunAllHintsAndModesWithWarmup() {
        BenchmarkResponse res = service.run(Report.TOP_PRODUCTS, null);

        assertThat(res.report()).isEqualTo("top-products");
        assertThat(res.results()).extracting(r -> r.source() + ":" + r.variant()).containsExactly(
                "oracle:AUTO", "oracle:NL", "oracle:HASH", "oracle:MERGE", "redis:AGGREGATE", "redis:CLIENT");
        assertThat(oracle.runs).hasSize(4 * 6);  // 1 warmup + 5 runs mỗi hint
        assertThat(redis.runs).hasSize(2 * 6);
        BenchmarkResult client = res.results().get(5);
        assertThat(client.runs()).isEqualTo(5);
        assertThat(client.samplesMs()).hasSize(5);
        assertThat(client.roundTrips()).isEqualTo(3);
        assertThat(oracle.runs).extracting(q -> q.joinHint()).containsSubsequence(
                JoinHint.AUTO, JoinHint.NL, JoinHint.HASH, JoinHint.MERGE);
        // Mặc định ngày: 12 tháng gần nhất theo +07:00, limit 10
        assertThat(oracle.runs.get(0).from()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(oracle.runs.get(0).to()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(oracle.runs.get(0).limit()).isEqualTo(10);
    }

    @Test
    void onlyAutoHintWhenJoinHintDoesNotApply() {
        BenchmarkResponse res = service.run(Report.REVENUE_BY_MONTH, null);
        assertThat(res.results()).extracting(r -> r.source() + ":" + r.variant())
                .containsExactly("oracle:AUTO", "redis:AGGREGATE", "redis:CLIENT");
    }

    @Test
    void unsupportedVariantsAreOmitted() {
        BenchmarkResponse res = service.run(Report.UNSOLD_PRODUCTS, null);
        assertThat(res.results()).extracting(BenchmarkResult::source).containsExactly("oracle");
        assertThat(redis.runs).hasSize(2); // dừng ngay ở lần warmup đầu của mỗi mode
    }

    @Test
    void explicitVariantsWarmupRunsAndDates() {
        BenchmarkRequest req = new BenchmarkRequest(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), 5, 0, 3,
                List.of(new BenchmarkVariant("Oracle", "hash"), new BenchmarkVariant("redis", "client")));
        BenchmarkResponse res = service.run(Report.TOP_PRODUCTS, req);

        assertThat(res.results()).extracting(r -> r.source() + ":" + r.variant() + ":" + r.runs())
                .containsExactly("oracle:HASH:3", "redis:CLIENT:3");
        assertThat(oracle.runs).hasSize(3).allSatisfy(q -> {
            assertThat(q.joinHint()).isEqualTo(JoinHint.HASH);
            assertThat(q.limit()).isEqualTo(5);
            assertThat(q.from()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(q.to()).isEqualTo(LocalDate.of(2026, 3, 31));
        });
        assertThat(redis.runs).hasSize(3).allSatisfy(q -> assertThat(q.redisMode()).isEqualTo(RedisMode.CLIENT));
    }

    @Test
    void invalidVariantOrDatesAre400() {
        assertThatThrownBy(() -> service.run(Report.TOP_PRODUCTS, new BenchmarkRequest(null, null, null, null, null,
                List.of(new BenchmarkVariant("oracle", "AGGREGATE")))))
                .isInstanceOf(ApiException.class)
                .hasMessage("variants[0].variant phải là AUTO, NL, HASH hoặc MERGE")
                .extracting(e -> ((ApiException) e).details().get("field")).isEqualTo("variants[0].variant");
        assertThatThrownBy(() -> service.run(Report.TOP_PRODUCTS, new BenchmarkRequest(null, null, null, null, null,
                List.of(new BenchmarkVariant("mongo", "AUTO")))))
                .extracting(e -> ((ApiException) e).details().get("field")).isEqualTo("variants[0].source");
        assertThatThrownBy(() -> service.run(Report.TOP_PRODUCTS, new BenchmarkRequest(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null, null, null, null)))
                .extracting(e -> ((ApiException) e).details().get("field")).isEqualTo("from");
    }

    @Test
    void csvHasOneRowPerSample() {
        BenchmarkResponse res = new BenchmarkResponse("top-products", List.of(
                new BenchmarkResult("oracle", "HASH", 2, 21.0, 1.0, 20.0, 22.0, 1, List.of(20.1, 21.0)),
                new BenchmarkResult("redis", "CLIENT", 1, 5.5, 0, 5.5, 5.5, 3, List.of(5.5))));
        assertThat(BenchmarkService.toCsv(res)).isEqualTo("""
                report,source,variant,run,elapsedMs,roundTrips
                top-products,oracle,HASH,1,20.1,1
                top-products,oracle,HASH,2,21.0,1
                top-products,redis,CLIENT,1,5.5,3
                """);
    }
}
