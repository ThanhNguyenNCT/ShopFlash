package com.shopflash.modules.indexing.repository.oracle;

import com.shopflash.modules.indexing.dto.response.ProductItem;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchResult;
import com.shopflash.modules.indexing.repository.OracleProductSearch;
import com.shopflash.modules.indexing.repository.oracle.OracleSearchSql.Sql;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tìm sản phẩm trên Oracle: trang + đếm (2 câu SQL). */
@Repository
public class OracleProductSearchRepository implements OracleProductSearch {

    /** Giá trị meta.variant của kết quả Oracle (giữ nguyên hợp đồng API). */
    static final String VARIANT = "AUTO";

    private final NamedParameterJdbcTemplate jdbc;

    public OracleProductSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        Sql page = OracleSearchSql.page(query);
        Sql count = OracleSearchSql.count(query);
        long started = System.nanoTime();
        List<ProductItem> items = jdbc.query(page.text(), page.params(), (rs, i) -> item(rs));
        Long total = jdbc.queryForObject(count.text(), count.params(), Long.class);
        double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
        return new SearchResult(items, total == null ? 0 : total, VARIANT, 2,
                page.text() + ";\n\n" + count.text(), elapsedMs);
    }

    static ProductItem item(ResultSet rs) throws SQLException {
        return new ProductItem(rs.getLong("product_id"), rs.getString("sku"), rs.getString("name"),
                rs.getLong("category_id"), rs.getString("category_name"), rs.getBigDecimal("base_price"),
                rs.getInt("in_stock") == 1, rs.getString("thumbnail_url"));
    }
}
