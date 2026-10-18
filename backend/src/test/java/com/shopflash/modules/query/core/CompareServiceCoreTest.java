package com.shopflash.modules.query.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflash.modules.query.dto.response.CompareResponse;
import com.shopflash.modules.query.dto.response.Mismatch;
import com.shopflash.modules.query.dto.response.PageInfo;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.ReportRow;
import com.shopflash.modules.query.dto.response.TopProductRow;
import com.shopflash.modules.query.dto.response.VipCustomerRow;
import com.shopflash.modules.query.dto.response.VipSummary;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.service.CompareService;
import com.shopflash.modules.query.service.ReportService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompareServiceCoreTest {

    static TopProductRow top(int rank, long id, String revenue) {
        return new TopProductRow(rank, id, "P" + id, "C", 10 - rank, new BigDecimal(revenue));
    }

    static CompareResponse compare(Report report, SourceResult oracle, SourceResult redis) {
        FakeReportSource o = new FakeReportSource(Source.ORACLE, q -> oracle);
        FakeReportSource r = new FakeReportSource(Source.REDIS, q -> redis);
        CompareService service = new CompareService(new ReportService(List.of(o, r), ReportServiceCoreTest.CLOCK));
        CompareResponse res = service.compare(ReportServiceCoreTest.query(report));
        assertThat(o.runs).hasSize(1);
        assertThat(r.runs).hasSize(1);
        return res;
    }

    static SourceResult rows(String variant, List<? extends ReportRow> rows) {
        return SourceResult.of(ReportData.of(rows), variant, 1, "S");
    }

    @Test
    void matchesWhenMoneyEqualAfterRoundingToTwoDecimals() {
        CompareResponse res = compare(Report.TOP_PRODUCTS,
                rows("AUTO", List.of(top(1, 10, "98000000"), top(2, 11, "1.004"))),
                rows("AGGREGATE", List.of(top(1, 10, "98000000.00"), top(2, 11, "1.00"))));
        assertThat(res.report()).isEqualTo("top-products");
        assertThat(res.match()).isTrue();
        assertThat(res.mismatches()).isEmpty();
        assertThat(res.oracle().meta().source()).isEqualTo("oracle");
        assertThat(res.redis().meta().source()).isEqualTo("redis");
    }

    @Test
    void reportsFieldMismatch() {
        CompareResponse res = compare(Report.TOP_PRODUCTS,
                rows("AUTO", List.of(top(1, 10456, "98000000"), top(2, 11, "1.006"))),
                rows("AGGREGATE", List.of(top(1, 10456, "97000000"), top(2, 11, "1.00"))));
        assertThat(res.match()).isFalse();
        assertThat(res.mismatches()).containsExactly(
                new Mismatch("10456", "revenue", new BigDecimal("98000000.00"), new BigDecimal("97000000.00")),
                new Mismatch("11", "revenue", new BigDecimal("1.01"), new BigDecimal("1.00")));
    }

    @Test
    void reportsMissingKeysOnBothSides() {
        TopProductRow a = top(1, 1, "10");
        TopProductRow b = top(2, 2, "10");
        TopProductRow c = new TopProductRow(2, 3, "P3", "C", 8, new BigDecimal("10"));
        CompareResponse res = compare(Report.TOP_PRODUCTS, rows("AUTO", List.of(a, b)), rows("AGGREGATE", List.of(a, c)));
        assertThat(res.match()).isFalse();
        assertThat(res.mismatches()).containsExactly(
                new Mismatch("2", "row", b, null),
                new Mismatch("3", "row", null, c));
    }

    @Test
    void reportsOrderDifference() {
        var m1 = new com.shopflash.modules.query.dto.response.MonthRevenueRow("2026-09", 1, BigDecimal.ONE);
        var m2 = new com.shopflash.modules.query.dto.response.MonthRevenueRow("2026-10", 1, BigDecimal.ONE);
        CompareResponse res = compare(Report.REVENUE_BY_MONTH, rows("AUTO", List.of(m1, m2)),
                rows("AGGREGATE", List.of(m2, m1)));
        assertThat(res.match()).isFalse();
        assertThat(res.mismatches()).containsExactly(
                new Mismatch("2026-09", "position", 1, 2),
                new Mismatch("2026-10", "position", 2, 1));
    }

    @Test
    void comparesPageTotalAndSummary() {
        VipCustomerRow v = new VipCustomerRow(1, "A", "a@x", 2, new BigDecimal("500"));
        SourceResult o = SourceResult.of(new ReportData(List.of(v), new PageInfo(1, 20, 10),
                new VipSummary(new BigDecimal("100.001"), 50)), "AUTO", 1, "S");
        SourceResult r = SourceResult.of(new ReportData(List.of(v), new PageInfo(1, 20, 11),
                new VipSummary(new BigDecimal("100.00"), 49)), "AGGREGATE", 2, "S");
        CompareResponse res = compare(Report.VIP_CUSTOMERS, o, r);
        assertThat(res.mismatches()).containsExactly(
                new Mismatch("page", "total", 10L, 11L),
                new Mismatch("summary", "customerCount", 50L, 49L));
    }

    @Test
    void limitsToFiveMismatches() {
        List<TopProductRow> o = new ArrayList<>();
        List<TopProductRow> r = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            o.add(top(i, i, "100"));
            r.add(top(i, i, "200"));
        }
        CompareResponse res = compare(Report.TOP_PRODUCTS, rows("AUTO", o), rows("AGGREGATE", r));
        assertThat(res.match()).isFalse();
        assertThat(res.mismatches()).hasSize(CompareService.MAX_MISMATCHES);
        assertThat(res.mismatches().get(0).key()).isEqualTo("1");
    }

    @Test
    void matchIsNullWhenOneSideUnsupported() {
        CompareResponse res = compare(Report.UNSOLD_PRODUCTS,
                rows("AUTO", List.of()), SourceResult.unsupported("AGGREGATE", "không hỗ trợ"));
        assertThat(res.match()).isNull();
        assertThat(res.mismatches()).isEmpty();
        assertThat(res.redis().data()).isNull();
        assertThat(res.redis().meta().supported()).isFalse();
    }
}
