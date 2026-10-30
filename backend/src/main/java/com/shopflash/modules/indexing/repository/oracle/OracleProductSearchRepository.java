package com.shopflash.modules.indexing.repository.oracle;

import com.shopflash.modules.indexing.dto.response.ExplainResponse;
import com.shopflash.modules.indexing.dto.response.ProductItem;
import com.shopflash.modules.indexing.model.OracleVariant;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;
import com.shopflash.modules.indexing.repository.OracleProductSearch;
import com.shopflash.modules.indexing.repository.oracle.OracleSearchSql.Sql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Repository;

/** Tìm sản phẩm trên Oracle: trang + đếm (2 câu SQL), và kế hoạch thực thi cho trang Lab. */
@Repository
public class OracleProductSearchRepository implements OracleProductSearch {

    private static final Logger log = LoggerFactory.getLogger(OracleProductSearchRepository.class);

    private static final String DISPLAY_CURSOR =
            "SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST'))";
    private static final String DISPLAY_EXPLAIN =
            "SELECT plan_table_output FROM TABLE(DBMS_XPLAN.DISPLAY(NULL, NULL, 'TYPICAL'))";

    private final NamedParameterJdbcTemplate jdbc;

    public OracleProductSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SearchResult search(SearchQuery query, OracleVariant variant) {
        Sql page = OracleSearchSql.page(query, variant, false);
        Sql count = OracleSearchSql.count(query, variant);
        long started = System.nanoTime();
        List<ProductItem> items = jdbc.query(page.text(), page.params(), (rs, i) -> item(rs));
        Long total = jdbc.queryForObject(count.text(), count.params(), Long.class);
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        return new SearchResult(items, total == null ? 0 : total, variant.name(), 2,
                page.text() + ";\n\n" + count.text(), elapsedMs);
    }

    /**
     * Kế hoạch thực thi thật của câu lấy trang: chạy câu (thêm GATHER_PLAN_STATISTICS) rồi đọc DISPLAY_CURSOR trên
     * cùng connection, nên có cả E-Rows, A-Rows, Buffers. Không đọc được thì lùi về EXPLAIN PLAN (chỉ ước lượng).
     */
    @Override
    public ExplainResponse explain(SearchQuery query, OracleVariant variant) {
        Sql executed = OracleSearchSql.page(query, variant, true);
        Sql explained = OracleSearchSql.page(query, variant, false);
        return jdbc.getJdbcTemplate().execute((ConnectionCallback<ExplainResponse>) con -> {
            NamedParameterJdbcTemplate single = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(con, true));
            try {
                single.query(executed.text(), executed.params(), (RowCallbackHandler) rs -> { });
                List<String> lines = single.getJdbcTemplate().queryForList(DISPLAY_CURSOR, String.class);
                if (lines.stream().anyMatch(l -> l != null && l.contains("Plan hash value"))) {
                    return new ExplainResponse("oracle", variant.name(), executed.text(), nonNull(lines));
                }
                log.warn("DISPLAY_CURSOR không trả kế hoạch, dùng EXPLAIN PLAN");
            } catch (DataAccessException e) {
                log.warn("DISPLAY_CURSOR lỗi, dùng EXPLAIN PLAN: {}", e.getMessage());
            }
            single.update("EXPLAIN PLAN FOR " + explained.text(), explained.params());
            List<String> lines = single.getJdbcTemplate().queryForList(DISPLAY_EXPLAIN, String.class);
            return new ExplainResponse("oracle", variant.name(), explained.text(), nonNull(lines));
        });
    }

    static ProductItem item(ResultSet rs) throws SQLException {
        return new ProductItem(rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"),
                rs.getLong("category_id"), rs.getString("category_name"), rs.getBigDecimal("base_price"),
                rs.getInt("in_stock") == 1, rs.getString("thumbnail_url"));
    }

    private static List<String> nonNull(List<String> lines) {
        return lines.stream().filter(Objects::nonNull).toList();
    }
}
