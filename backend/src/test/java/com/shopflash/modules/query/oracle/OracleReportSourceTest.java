package com.shopflash.modules.query.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflash.modules.query.dto.request.StatsRequest;
import com.shopflash.modules.query.dto.response.CategoryRevenueRow;
import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.dto.response.ReportRow;
import com.shopflash.modules.query.dto.response.StatsResponse;
import com.shopflash.modules.query.dto.response.TopProductRow;
import com.shopflash.modules.query.dto.response.UnsoldProductRow;
import com.shopflash.modules.query.dto.response.VipCustomerRow;
import com.shopflash.modules.query.dto.response.VipSummary;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.oracle.OracleReportSource;
import com.shopflash.modules.query.service.OracleStatsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Chạy trên Oracle thật (docker compose, .env ở thư mục gốc repo). Dữ liệu có thể chỉ là seed lõi hoặc
 * cả bộ sinh 100k đơn, nên chỉ kiểm tra tính nhất quán, không so số cố định.
 */
@SpringBootTest // MOCK: SecurityConfig cần context web (HttpSecurity)
class OracleReportSourceTest {

    private static final LocalDate FROM = LocalDate.of(2000, 1, 1);
    private static final LocalDate TO = LocalDate.of(2099, 12, 31);

    @Autowired
    OracleReportSource source;

    @Autowired
    OracleStatsService stats;

    @Autowired
    JdbcTemplate jdbc;

    private static ReportQuery query(Report report, JoinHint hint) {
        return new ReportQuery(report, FROM, TO, hint, RedisMode.AGGREGATE, 10, 1, 20);
    }

    @Test
    void revenueByMonthMatchesCountedOrders() {
        SourceResult result = source.run(query(Report.REVENUE_BY_MONTH, JoinHint.HASH));
        assertThat(result.supported()).isTrue();
        assertThat(result.variant()).isEqualTo("AUTO");
        assertThat(result.roundTrips()).isEqualTo(1);
        assertThat(result.statement()).contains(":fromTs").doesNotContain("/*+");

        List<MonthRevenueRow> rows = rows(result, MonthRevenueRow.class);
        assertThat(rows).extracting(MonthRevenueRow::month).isSorted();
        BigDecimal expected = jdbc.queryForObject(
                "SELECT NVL(SUM(total_amount), 0) FROM orders WHERE status IN ('CONFIRMED', 'COMPLETED')",
                BigDecimal.class);
        assertThat(rows.stream().map(MonthRevenueRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(expected);
        rows.forEach(r -> assertThat(r.revenue().scale()).isEqualTo(2));
    }

    @Test
    void revenueByCategorySameRowsForAllHints() {
        List<CategoryRevenueRow> auto = rows(source.run(query(Report.REVENUE_BY_CATEGORY, JoinHint.AUTO)),
                CategoryRevenueRow.class);
        BigDecimal lines = jdbc.queryForObject("""
                SELECT NVL(SUM(oi.line_total), 0) FROM order_items oi JOIN orders o ON o.order_id = oi.order_id
                WHERE o.status IN ('CONFIRMED', 'COMPLETED')""", BigDecimal.class);
        assertThat(auto.stream().map(CategoryRevenueRow::revenue).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(lines);
        for (int i = 1; i < auto.size(); i++) {
            assertThat(auto.get(i - 1).revenue()).isGreaterThanOrEqualTo(auto.get(i).revenue());
        }
        for (JoinHint hint : JoinHint.values()) {
            SourceResult result = source.run(query(Report.REVENUE_BY_CATEGORY, hint));
            assertThat(result.variant()).isEqualTo(hint.name());
            assertThat(rows(result, CategoryRevenueRow.class)).isEqualTo(auto);
        }
    }

    @Test
    void topProductsAllHintsAgreeAndRanked() {
        List<TopProductRow> auto = rows(source.run(query(Report.TOP_PRODUCTS, JoinHint.AUTO)), TopProductRow.class);
        assertThat(auto.size()).isLessThanOrEqualTo(10);
        for (int i = 0; i < auto.size(); i++) {
            assertThat(auto.get(i).rank()).isEqualTo(i + 1);
            if (i > 0) {
                assertThat(auto.get(i - 1).quantity()).isGreaterThanOrEqualTo(auto.get(i).quantity());
            }
        }
        for (JoinHint hint : JoinHint.values()) {
            SourceResult result = source.run(query(Report.TOP_PRODUCTS, hint));
            assertThat(rows(result, TopProductRow.class)).isEqualTo(auto);
            if (hint != JoinHint.AUTO) {
                assertThat(result.statement()).contains("/*+ LEADING(o oi p c)");
            }
        }
    }

    @Test
    void vipCustomersAboveAverage() {
        SourceResult result = source.run(query(Report.VIP_CUSTOMERS, JoinHint.NL));
        assertThat(result.roundTrips()).isEqualTo(2);
        assertThat(result.statement()).contains("HAVING SUM(o.total_amount) > (");
        VipSummary summary = (VipSummary) result.data().summary();
        List<VipCustomerRow> rows = rows(result, VipCustomerRow.class);
        assertThat(result.data().page().total()).isLessThanOrEqualTo(summary.customerCount());
        assertThat(rows.size()).isLessThanOrEqualTo((int) Math.min(20, result.data().page().total()));
        rows.forEach(r -> assertThat(r.totalSpent()).isGreaterThan(summary.averageSpent()));
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i - 1).totalSpent()).isGreaterThanOrEqualTo(rows.get(i).totalSpent());
        }
        for (JoinHint hint : JoinHint.values()) {
            assertThat(rows(source.run(query(Report.VIP_CUSTOMERS, hint)), VipCustomerRow.class)).isEqualTo(rows);
        }
    }

    @Test
    void unsoldProductsPagedAndSorted() {
        SourceResult result = source.run(query(Report.UNSOLD_PRODUCTS, JoinHint.MERGE));
        assertThat(result.variant()).isEqualTo("AUTO");
        List<UnsoldProductRow> rows = rows(result, UnsoldProductRow.class);
        assertThat(rows).extracting(UnsoldProductRow::productId).isSorted();
        assertThat(result.data().page().total()).isGreaterThanOrEqualTo(rows.size());
        Integer sold = jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id = ?",
                Integer.class, rows.isEmpty() ? -1L : rows.get(0).productId());
        assertThat(sold).isZero();
    }

    @Test
    void planShowsForcedJoinMethod() {
        PlanResponse nl = source.plan(query(Report.TOP_PRODUCTS, JoinHint.NL));
        PlanResponse hash = source.plan(query(Report.TOP_PRODUCTS, JoinHint.HASH));
        PlanResponse merge = source.plan(query(Report.TOP_PRODUCTS, JoinHint.MERGE));
        String nlText = String.join("\n", nl.lines());
        String hashText = String.join("\n", hash.lines());
        String mergeText = String.join("\n", merge.lines());
        System.out.println(nlText + "\n" + hashText + "\n" + mergeText);

        assertThat(nl.source()).isEqualTo("oracle");
        assertThat(nl.variant()).isEqualTo("NL");
        assertThat(nlText).contains("NESTED LOOPS").doesNotContain("HASH JOIN").doesNotContain("MERGE JOIN");
        assertThat(hashText).contains("HASH JOIN").doesNotContain("NESTED LOOPS");
        assertThat(mergeText).contains("MERGE JOIN");
        assertThat(nlText).contains("A-Rows");
    }

    /** DELETE rồi GATHER lại ngay trong cùng test: DB dùng chung, không được để bảng mất thống kê. */
    @Test
    void deleteThenGatherStats() {
        try {
            StatsResponse deleted = stats.apply(StatsRequest.Action.DELETE);
            assertThat(deleted.action()).isEqualTo("DELETE");
            deleted.tables().forEach(t -> {
                assertThat(t.numRows()).isNull();
                assertThat(t.lastAnalyzed()).isNull();
            });
        } finally {
            stats.apply(StatsRequest.Action.GATHER);
        }
        StatsResponse response = stats.apply(StatsRequest.Action.GATHER);
        assertThat(response.action()).isEqualTo("GATHER");
        assertThat(response.tables()).extracting(StatsResponse.TableStats::table)
                .containsExactly("ORDERS", "ORDER_ITEMS", "PRODUCTS", "CATEGORIES", "USERS");
        response.tables().forEach(t -> {
            assertThat(t.numRows()).isNotNull();
            assertThat(t.lastAnalyzed()).isNotNull();
            assertThat(t.lastAnalyzed().getOffset()).isEqualTo(ReportQuery.ZONE);
        });
        assertThat(response.elapsedMs()).isPositive();
    }

    @SuppressWarnings("unchecked")
    private static <T extends ReportRow> List<T> rows(SourceResult result, Class<T> type) {
        assertThat(result.data().rows()).allMatch(type::isInstance);
        return (List<T>) result.data().rows();
    }
}
