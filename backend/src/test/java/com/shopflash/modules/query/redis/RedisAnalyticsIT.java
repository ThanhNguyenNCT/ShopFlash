package com.shopflash.modules.query.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.shopflash.modules.query.dto.response.AnalyticsStatusResponse;
import com.shopflash.modules.query.dto.response.CategoryRevenueRow;
import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.dto.response.RebuildResponse;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.TopProductRow;
import com.shopflash.modules.query.dto.response.VipCustomerRow;
import com.shopflash.modules.query.dto.response.VipSummary;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.redis.RedisCommandRunner;
import com.shopflash.modules.query.repository.redis.RedisReplies;
import com.shopflash.modules.query.repository.redis.RedisReportSource;
import com.shopflash.modules.query.service.AnalyticsProjectionService;

/**
 * Chạy trên stack Docker thật (Oracle SHOP + Redis Stack, đọc ../.env).
 * Chạy: ./mvnw -q test -Dtest='Redis*'
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RedisAnalyticsIT {

    private static final String COUNTED = "o.status IN ('CONFIRMED', 'COMPLETED')";
    private static final String RANGE = "o.created_at >= :fromTs AND o.created_at < :toTs";

    @Autowired
    AnalyticsProjectionService projection;
    @Autowired
    RedisReportSource redis;
    @Autowired
    DataSource dataSource;
    @Autowired
    RedisCommandRunner commands;

    private static final String STALE_ORDER = "order:analytics:999999999999";

    NamedParameterJdbcTemplate jdbc;
    LocalDate minDay;
    LocalDate maxDay;

    @BeforeAll
    void rebuild() {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        // Key không còn trong Oracle phải bị rebuild xóa.
        commands.call("JSON.SET", STALE_ORDER, "$",
                "{\"orderId\":-1,\"userId\":-1,\"status\":\"COMPLETED\",\"orderType\":\"NORMAL\",\"totalAmount\":1,"
                        + "\"createdAt\":0,\"items\":[]}");
        RebuildResponse r = projection.rebuild();
        System.out.println("[redis-it] rebuild " + r);
        assertThat(r.orders()).isPositive();
        assertThat(RedisReplies.asLong(commands.call("EXISTS", STALE_ORDER))).isZero();
        Map<String, Object> range = jdbc.queryForMap("""
                SELECT TO_CHAR(MIN(o.created_at) AT TIME ZONE '+07:00', 'YYYY-MM-DD') AS min_day,
                       TO_CHAR(MAX(o.created_at) AT TIME ZONE '+07:00', 'YYYY-MM-DD') AS max_day
                  FROM orders o WHERE """ + " " + COUNTED, Map.of());
        minDay = LocalDate.parse((String) range.get("MIN_DAY"));
        maxDay = LocalDate.parse((String) range.get("MAX_DAY"));
    }

    private ReportQuery query(Report report, LocalDate from, LocalDate to, RedisMode mode) {
        return new ReportQuery(report, from, to, JoinHint.AUTO, mode, 10, 1, 20);
    }

    /** Toàn bộ dữ liệu và một khoảng con (để kiểm tra mốc ngày +07:00). */
    private List<LocalDate[]> ranges() {
        LocalDate subFrom = maxDay.minusMonths(3).withDayOfMonth(15);
        return List.of(new LocalDate[] {minDay, maxDay},
                new LocalDate[] {subFrom.isBefore(minDay) ? minDay : subFrom, maxDay.minusDays(10)});
    }

    private MapSqlParameterSource params(ReportQuery q) {
        return new MapSqlParameterSource()
                .addValue("fromTs", q.fromInstant().atOffset(ReportQuery.ZONE))
                .addValue("toTs", q.toExclusiveInstant().atOffset(ReportQuery.ZONE))
                .addValue("lim", q.limit());
    }

    private static BigDecimal money(Object v) {
        return ((BigDecimal) v).setScale(2, RoundingMode.HALF_UP);
    }

    private ReportData runBoth(ReportQuery q) {
        SourceResult agg = redis.run(q.withRedisMode(RedisMode.AGGREGATE));
        SourceResult client = redis.run(q.withRedisMode(RedisMode.CLIENT));
        System.out.printf("[redis-it] %s %s..%s AGGREGATE roundTrips=%d CLIENT roundTrips=%d rows=%d%n",
                q.report().slug(), q.from(), q.to(), agg.roundTrips(), client.roundTrips(), agg.data().rows().size());
        System.out.println("[redis-it] AGGREGATE statement:\n" + agg.statement());
        System.out.println("[redis-it] CLIENT statement:\n" + client.statement());
        assertThat(agg.supported()).isTrue();
        assertThat(agg.variant()).isEqualTo("AGGREGATE");
        assertThat(client.variant()).isEqualTo("CLIENT");
        assertThat(client.data()).isEqualTo(agg.data());
        return agg.data();
    }

    @Test
    void statusInSyncAfterRebuild() {
        AnalyticsStatusResponse s = projection.status();
        System.out.println("[redis-it] status " + s);
        assertThat(s.inSync()).isTrue();
        assertThat(s.redis().lastRebuildAt()).isNotNull();
        assertThat(s.redis().indexing()).isFalse();
    }

    @Test
    void revenueByMonthMatchesOracle() {
        for (LocalDate[] r : ranges()) {
            ReportQuery q = query(Report.REVENUE_BY_MONTH, r[0], r[1], RedisMode.AGGREGATE);
            List<MonthRevenueRow> expected = jdbc.query("""
                    SELECT TO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM') AS month,
                           COUNT(*) AS order_count, SUM(o.total_amount) AS revenue
                      FROM orders o WHERE """ + " " + COUNTED + " AND " + RANGE + " " + """
                     GROUP BY TO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM') ORDER BY month""",
                    params(q), (rs, i) -> new MonthRevenueRow(rs.getString(1), rs.getLong(2),
                            money(rs.getBigDecimal(3))));
            assertThat(runBoth(q).rows()).isEqualTo(expected).isNotEmpty();
        }
    }

    @Test
    void revenueByCategoryMatchesOracle() {
        for (LocalDate[] r : ranges()) {
            ReportQuery q = query(Report.REVENUE_BY_CATEGORY, r[0], r[1], RedisMode.AGGREGATE);
            List<CategoryRevenueRow> expected = jdbc.query("""
                    SELECT c.category_id, c.name, SUM(oi.quantity), SUM(oi.line_total) AS revenue
                      FROM orders o JOIN order_items oi ON oi.order_id = o.order_id
                      JOIN products p ON p.product_id = oi.product_id
                      JOIN categories c ON c.category_id = p.category_id
                     WHERE """ + " " + COUNTED + " AND " + RANGE + " " + """
                     GROUP BY c.category_id, c.name ORDER BY revenue DESC, c.category_id""",
                    params(q), (rs, i) -> new CategoryRevenueRow(rs.getLong(1), rs.getString(2), rs.getLong(3),
                            money(rs.getBigDecimal(4))));
            assertThat(runBoth(q).rows()).isEqualTo(expected).isNotEmpty();
        }
    }

    @Test
    void topProductsMatchesOracle() {
        for (LocalDate[] r : ranges()) {
            ReportQuery q = query(Report.TOP_PRODUCTS, r[0], r[1], RedisMode.AGGREGATE);
            List<TopProductRow> expected = jdbc.query("""
                    SELECT p.product_id, p.name, c.name, SUM(oi.quantity) AS qty, SUM(oi.line_total)
                      FROM orders o JOIN order_items oi ON oi.order_id = o.order_id
                      JOIN products p ON p.product_id = oi.product_id
                      JOIN categories c ON c.category_id = p.category_id
                     WHERE """ + " " + COUNTED + " AND " + RANGE + " " + """
                     GROUP BY p.product_id, p.name, c.name ORDER BY qty DESC, p.product_id
                     FETCH FIRST :lim ROWS ONLY""",
                    params(q), (rs, i) -> new TopProductRow(i + 1, rs.getLong(1), rs.getString(2), rs.getString(3),
                            rs.getLong(4), money(rs.getBigDecimal(5))));
            assertThat(runBoth(q).rows()).isEqualTo(expected).hasSize(q.limit());
        }
    }

    @Test
    void vipCustomersMatchesOracle() {
        for (LocalDate[] r : ranges()) {
            for (int page : new int[] {1, 3}) {
                ReportQuery q = new ReportQuery(Report.VIP_CUSTOMERS, r[0], r[1], JoinHint.AUTO,
                        RedisMode.AGGREGATE, 10, page, 20);
                String perUser = """
                        SELECT o.user_id, SUM(o.total_amount) AS spent, COUNT(*) AS cnt
                          FROM orders o WHERE """ + " " + COUNTED + " AND " + RANGE + " GROUP BY o.user_id";
                Map<String, Object> summary = jdbc.queryForMap(
                        "SELECT AVG(spent) AS avg_spent, COUNT(*) AS customers FROM (" + perUser + ")", params(q));
                String vipSql = """
                        SELECT t.user_id, u.full_name, u.email, t.cnt, t.spent
                          FROM (%s) t JOIN users u ON u.user_id = t.user_id
                         WHERE t.spent > (SELECT AVG(spent) FROM (%s))""".formatted(perUser, perUser);
                List<VipCustomerRow> expected = jdbc.query(vipSql + " " + """
                         ORDER BY t.spent DESC, t.user_id OFFSET :off ROWS FETCH NEXT :size ROWS ONLY""",
                        params(q).addValue("off", q.offset()).addValue("size", q.size()),
                        (rs, i) -> new VipCustomerRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                                money(rs.getBigDecimal(5))));
                Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + vipSql + ")", params(q), Long.class);

                ReportData data = runBoth(q);
                assertThat(data.rows()).isEqualTo(expected).isNotEmpty();
                assertThat(data.page().total()).isEqualTo(total);
                VipSummary s = (VipSummary) data.summary();
                assertThat(s.customerCount()).isEqualTo(((Number) summary.get("CUSTOMERS")).longValue());
                assertThat(s.averageSpent()).isEqualByComparingTo(money(summary.get("AVG_SPENT")));
            }
        }
    }

    @Test
    void unsoldProductsUnsupported() {
        for (RedisMode mode : RedisMode.values()) {
            SourceResult r = redis.run(query(Report.UNSOLD_PRODUCTS, minDay, maxDay, mode));
            assertThat(r.supported()).isFalse();
            assertThat(r.data()).isNull();
            assertThat(r.variant()).isEqualTo(mode.name());
            assertThat(r.note()).contains("NOT EXISTS");
        }
    }

    @Test
    void plansForBothModes() {
        for (Report report : List.of(Report.REVENUE_BY_MONTH, Report.REVENUE_BY_CATEGORY, Report.TOP_PRODUCTS,
                Report.VIP_CUSTOMERS)) {
            PlanResponse agg = redis.plan(query(report, minDay, maxDay, RedisMode.AGGREGATE));
            PlanResponse client = redis.plan(query(report, minDay, maxDay, RedisMode.CLIENT));
            System.out.println("[redis-it] plan " + report.slug() + " AGGREGATE:\n  "
                    + String.join("\n  ", agg.lines()));
            System.out.println("[redis-it] plan " + report.slug() + " CLIENT:\n  "
                    + String.join("\n  ", client.lines()));
            assertThat(agg.statement()).contains("FT.PROFILE");
            assertThat(agg.lines()).hasSizeGreaterThan(3);
            assertThat(client.statement()).contains("WITHCURSOR");
            assertThat(client.lines()).isNotEmpty();
        }
    }
}
