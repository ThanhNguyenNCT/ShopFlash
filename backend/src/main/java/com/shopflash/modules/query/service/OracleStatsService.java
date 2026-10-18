package com.shopflash.modules.query.service;

import com.shopflash.modules.query.dto.request.StatsRequest;
import com.shopflash.modules.query.dto.response.StatsResponse;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.ReportQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Gather/Delete thống kê optimizer cho các bảng báo cáo của schema hiện tại (SHOP), để demo CBO đổi plan
 * khi có/không có thống kê. Tên bảng là danh sách cố định, truyền vào PL/SQL bằng bind variable.
 */
@Service
public class OracleStatsService implements OracleStatsOperations {

    static final List<String> TABLES = List.of("ORDERS", "ORDER_ITEMS", "PRODUCTS", "CATEGORIES", "USERS");

    private static final String GATHER =
            "BEGIN DBMS_STATS.GATHER_TABLE_STATS(ownname => USER, tabname => :tableName, cascade => TRUE); END;";
    private static final String DELETE =
            "BEGIN DBMS_STATS.DELETE_TABLE_STATS(ownname => USER, tabname => :tableName, cascade_indexes => TRUE); END;";

    /** last_analyzed là DATE theo giờ của server DB; gắn múi giờ của server rồi đổi sang +07:00. */
    private static final String READ_STATS = """
            SELECT table_name,
                   num_rows,
                   FROM_TZ(CAST(last_analyzed AS TIMESTAMP), TO_CHAR(SYSTIMESTAMP, 'TZR')) AS last_analyzed
            FROM user_tables
            WHERE table_name IN (:tables)""";

    private final NamedParameterJdbcTemplate jdbc;

    public OracleStatsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StatsResponse apply(StatsRequest.Action action) {
        String block = action == StatsRequest.Action.GATHER ? GATHER : DELETE;
        long start = System.nanoTime();
        try {
            for (String table : TABLES) {
                jdbc.update(block, new MapSqlParameterSource("tableName", table));
            }
            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
            return new StatsResponse(action.name(), readStats(), elapsedMs);
        } catch (DataAccessException e) {
            throw new ApiException(ErrorCode.ORACLE_ERROR,
                    "Oracle lỗi khi " + action.name() + " thống kê: " + e.getMostSpecificCause().getMessage(), null, e);
        }
    }

    private List<StatsResponse.TableStats> readStats() {
        Map<String, StatsResponse.TableStats> byName = jdbc.query(READ_STATS,
                        new MapSqlParameterSource("tables", TABLES),
                        (rs, i) -> {
                            long numRows = rs.getLong("num_rows");
                            Long rows = rs.wasNull() ? null : numRows;
                            OffsetDateTime analyzed = rs.getObject("last_analyzed", OffsetDateTime.class);
                            return new StatsResponse.TableStats(rs.getString("table_name"), rows,
                                    analyzed == null ? null : analyzed.withOffsetSameInstant(ReportQuery.ZONE));
                        })
                .stream()
                .collect(Collectors.toMap(StatsResponse.TableStats::table, Function.identity()));
        return TABLES.stream()
                .map(t -> byName.getOrDefault(t, new StatsResponse.TableStats(t, null, null)))
                .toList();
    }
}
