package com.shopflash.modules.query.repository.oracle;

import com.shopflash.modules.query.dto.response.CategoryRevenueRow;
import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.PageInfo;
import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.TopProductRow;
import com.shopflash.modules.query.dto.response.UnsoldProductRow;
import com.shopflash.modules.query.dto.response.VipCustomerRow;
import com.shopflash.modules.query.dto.response.VipSummary;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.ReportSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Repository;

/** Báo cáo F5/F6 chạy trực tiếp trên Oracle (schema SHOP). Câu SQL trong {@link OracleReportSql}. */
@Repository
public class OracleReportSource implements ReportSource {

    private static final Logger log = LoggerFactory.getLogger(OracleReportSource.class);

    private static final String DISPLAY_CURSOR =
            "SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'))";
    private static final String DISPLAY_EXPLAIN =
            "SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, NULL, 'TYPICAL'))";

    private final NamedParameterJdbcTemplate jdbc;

    public OracleReportSource(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Source source() {
        return Source.ORACLE;
    }

    @Override
    public SourceResult run(ReportQuery query) {
        Report report = query.report();
        JoinHint hint = effectiveHint(query);
        String sql = OracleReportSql.render(report, hint, false);
        MapSqlParameterSource params = params(query);
        try {
            return switch (report) {
                case REVENUE_BY_MONTH -> SourceResult.of(
                        ReportData.of(jdbc.query(sql, params, (rs, i) -> new MonthRevenueRow(
                                rs.getString("month"), rs.getLong("order_count"), money(rs, "revenue")))),
                        hint.name(), 1, sql);
                case REVENUE_BY_CATEGORY -> SourceResult.of(
                        ReportData.of(jdbc.query(sql, params, (rs, i) -> new CategoryRevenueRow(
                                rs.getLong("category_id"), rs.getString("category_name"),
                                rs.getLong("quantity"), money(rs, "revenue")))),
                        hint.name(), 1, sql);
                case TOP_PRODUCTS -> SourceResult.of(
                        ReportData.of(jdbc.query(sql, params, (rs, i) -> new TopProductRow(
                                i + 1, rs.getLong("product_id"), rs.getString("product_name"),
                                rs.getString("category_name"), rs.getLong("quantity"), money(rs, "revenue")))),
                        hint.name(), 1, sql);
                case VIP_CUSTOMERS -> vipCustomers(query, hint, sql, params);
                case UNSOLD_PRODUCTS -> unsoldProducts(query, hint, sql, params);
            };
        } catch (DataAccessException e) {
            throw oracleError("chạy báo cáo " + report.slug(), e);
        }
    }

    private SourceResult vipCustomers(ReportQuery query, JoinHint hint, String sql, MapSqlParameterSource params) {
        List<VipCustomerRow> rows = jdbc.query(sql, params, (rs, i) -> new VipCustomerRow(
                rs.getLong("user_id"), rs.getString("full_name"), rs.getString("email"),
                rs.getLong("order_count"), money(rs, "total_spent")));
        long[] vipCount = new long[1];
        VipSummary summary = jdbc.queryForObject(OracleReportSql.VIP_SUMMARY, params, (rs, i) -> {
            vipCount[0] = rs.getLong("vip_count");
            BigDecimal avg = rs.getBigDecimal("average_spent");
            return new VipSummary(avg == null ? BigDecimal.ZERO.setScale(2) : scale(avg), rs.getLong("customer_count"));
        });
        PageInfo page = new PageInfo(query.page(), query.size(), vipCount[0]);
        return SourceResult.of(new ReportData(rows, page, summary), hint.name(), 2,
                sql + ";\n\n" + OracleReportSql.VIP_SUMMARY);
    }

    private SourceResult unsoldProducts(ReportQuery query, JoinHint hint, String sql, MapSqlParameterSource params) {
        List<UnsoldProductRow> rows = jdbc.query(sql, params, (rs, i) -> new UnsoldProductRow(
                rs.getLong("product_id"), rs.getString("product_name"), rs.getString("category_name"),
                money(rs, "base_price")));
        Long total = jdbc.queryForObject(OracleReportSql.UNSOLD_COUNT, params, Long.class);
        PageInfo page = new PageInfo(query.page(), query.size(), total == null ? 0 : total);
        return SourceResult.of(new ReportData(rows, page, null), hint.name(), 2,
                sql + ";\n\n" + OracleReportSql.UNSOLD_COUNT);
    }

    /**
     * Kế hoạch thực thi thật: chạy câu SQL (cùng hint, thêm GATHER_PLAN_STATISTICS) trên một connection rồi đọc
     * DBMS_XPLAN.DISPLAY_CURSOR(ALLSTATS LAST) ngay trên connection đó, nên có cả E-Rows lẫn A-Rows.
     * Nếu không đọc được cursor (thiếu quyền V$...), lùi về EXPLAIN PLAN + DBMS_XPLAN.DISPLAY (chỉ ước lượng).
     */
    @Override
    public PlanResponse plan(ReportQuery query) {
        Report report = query.report();
        JoinHint hint = effectiveHint(query);
        String executed = OracleReportSql.render(report, hint, true);
        String explained = OracleReportSql.render(report, hint, false);
        MapSqlParameterSource params = params(query);
        try {
            PlanResponse plan = jdbc.getJdbcTemplate().execute((ConnectionCallback<PlanResponse>) con -> {
                NamedParameterJdbcTemplate single =
                        new NamedParameterJdbcTemplate(new SingleConnectionDataSource(con, true));
                try {
                    // Đọc hết các dòng để A-Rows phản ánh trọn lần chạy.
                    single.query(executed, params, (RowCallbackHandler) rs -> { });
                    List<String> lines = single.getJdbcTemplate().queryForList(DISPLAY_CURSOR, String.class);
                    if (lines.stream().anyMatch(l -> l != null && l.contains("Plan hash value"))) {
                        return new PlanResponse("oracle", hint.name(), executed, nonNull(lines));
                    }
                    log.warn("DISPLAY_CURSOR không trả plan cho {}, dùng EXPLAIN PLAN", report.slug());
                } catch (DataAccessException e) {
                    log.warn("DISPLAY_CURSOR lỗi cho {}, dùng EXPLAIN PLAN: {}", report.slug(), e.getMessage());
                }
                single.update("EXPLAIN PLAN FOR " + explained, params);
                List<String> lines = single.getJdbcTemplate().queryForList(DISPLAY_EXPLAIN, String.class);
                return new PlanResponse("oracle", hint.name(), explained, nonNull(lines));
            });
            return plan;
        } catch (DataAccessException e) {
            throw oracleError("lấy kế hoạch thực thi " + report.slug(), e);
        }
    }

    private static JoinHint effectiveHint(ReportQuery query) {
        JoinHint hint = query.joinHint() == null ? JoinHint.AUTO : query.joinHint();
        return query.report().joinHintApplies() ? hint : JoinHint.AUTO;
    }

    private static MapSqlParameterSource params(ReportQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", query.limit())
                .addValue("offset", query.offset())
                .addValue("size", query.size());
        if (query.from() != null && query.to() != null) {
            params.addValue("fromTs", OffsetDateTime.ofInstant(query.fromInstant(), ReportQuery.ZONE))
                    .addValue("toExclusiveTs", OffsetDateTime.ofInstant(query.toExclusiveInstant(), ReportQuery.ZONE));
        }
        return params;
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO.setScale(2) : scale(value);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static List<String> nonNull(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(line == null ? "" : line);
        }
        return out;
    }

    private static ApiException oracleError(String action, DataAccessException e) {
        Throwable cause = e.getMostSpecificCause();
        return new ApiException(ErrorCode.ORACLE_ERROR,
                "Oracle lỗi khi " + action + ": " + cause.getMessage(), null, e);
    }
}
