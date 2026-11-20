package com.shopflash.modules.indexing.repository.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SortOrder;
import com.shopflash.modules.indexing.repository.oracle.OracleSearchSql.Sql;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OracleSearchSqlCoreTest {

    static SearchQuery browse(Long categoryId, boolean inStock, SortOrder sort) {
        return new SearchQuery(null, List.of(), categoryId, new BigDecimal("1000000"), new BigDecimal("5000000"),
                inStock, sort, 2, 24);
    }

    static SearchQuery keyword(SortOrder sort) {
        return new SearchQuery("sony pro", List.of("sony", "pro"), null, null, null, false, sort, 1, 24);
    }

    @Test
    void browseUsesBindsAndPriceOrderWithTieBreaker() {
        Sql page = OracleSearchSql.page(browse(5L, false, SortOrder.PRICE_ASC));
        assertThat(page.text())
                .startsWith("SELECT p.product_id")
                .contains("p.status = 'ACTIVE'", "p.category_id = :categoryId", "p.base_price >= :minPrice",
                        "p.base_price <= :maxPrice", "ORDER BY p.base_price, p.product_id",
                        "OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY")
                .doesNotContain("CONTAINS", "/*+", "5000000");
        assertThat(page.params().getValue("categoryId")).isEqualTo(5L);
        assertThat(page.params().getValue("offset")).isEqualTo(24);
        assertThat(page.params().getValue("size")).isEqualTo(24);
    }

    @Test
    void priceDescendingOrder() {
        Sql page = OracleSearchSql.page(browse(null, false, SortOrder.PRICE_DESC));
        assertThat(page.text()).contains("ORDER BY p.base_price DESC, p.product_id DESC")
                .doesNotContain("p.category_id = :categoryId");
    }

    @Test
    void countOnlyJoinsWhenFilteringInStock() {
        Sql plain = OracleSearchSql.count(browse(5L, false, SortOrder.PRICE_ASC));
        assertThat(plain.text()).startsWith("SELECT COUNT(*)").contains("FROM products p").doesNotContain("JOIN");
        Sql inStock = OracleSearchSql.count(browse(5L, true, SortOrder.PRICE_ASC));
        assertThat(inStock.text()).contains("JOIN categories c", "JOIN product_inventory i", "c.status = 'ACTIVE'",
                "i.on_hand_quantity - i.reserved_flash_quantity > 0");
    }

    @Test
    void keywordUsesOracleTextAndScore() {
        Sql page = OracleSearchSql.page(keyword(SortOrder.RELEVANCE));
        assertThat(page.text()).contains("CONTAINS(p.name, :textQuery, 1) > 0", "ORDER BY SCORE(1) DESC, p.product_id");
        assertThat(page.params().getValue("textQuery")).isEqualTo("sony% AND pro%");
        Sql count = OracleSearchSql.count(keyword(SortOrder.RELEVANCE));
        assertThat(count.text()).contains("CONTAINS(p.name, :textQuery) > 0");
        Sql byPrice = OracleSearchSql.page(keyword(SortOrder.PRICE_ASC));
        assertThat(byPrice.text()).contains("CONTAINS(p.name, :textQuery) > 0").doesNotContain("SCORE(1)");
    }
}
