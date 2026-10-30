package com.shopflash.modules.indexing.repository.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflash.modules.indexing.dto.response.ProductItem;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SortOrder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RedisSearchCommandCoreTest {

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void buildsQueryFromKeywordsAndFilters() {
        SearchQuery q = new SearchQuery("Sony Pro", List.of("sony", "pro"), 5L, new BigDecimal("1000000.00"), null,
                true, SortOrder.RELEVANCE, 1, 24);
        assertThat(RedisSearchCommand.query(q)).isEqualTo(
                "@name:(sony* pro*) @status:{ACTIVE} @categoryId:[5 5] @price:[1000000 +inf] @inStock:{true}");
    }

    @Test
    void browseQueryWithoutKeywords() {
        SearchQuery q = new SearchQuery(null, List.of(), null, null, new BigDecimal("5000000"), false,
                SortOrder.PRICE_ASC, 1, 24);
        assertThat(RedisSearchCommand.query(q)).isEqualTo("@status:{ACTIVE} @price:[-inf 5000000]");
    }

    @Test
    void searchArgumentsSortPageAndTimeout() {
        SearchQuery desc = new SearchQuery("pro", List.of("pro"), null, null, null, false, SortOrder.PRICE_DESC, 2, 24);
        assertThat(RedisSearchCommand.search(desc)).containsExactly("FT.SEARCH", "idx:products",
                "@name:(pro*) @status:{ACTIVE}", "SORTBY", "price", "DESC", "LIMIT", "24", "24", "TIMEOUT", "0");
        SearchQuery relevance = new SearchQuery("pro", List.of("pro"), null, null, null, false, SortOrder.RELEVANCE, 1, 24);
        assertThat(RedisSearchCommand.search(relevance)).doesNotContain("SORTBY");
        assertThat(RedisSearchCommand.profile(relevance)).startsWith("FT.PROFILE", "idx:products", "SEARCH", "QUERY");
    }

    @Test
    void parsesSearchReply() {
        Object reply = List.of(2L,
                b("product:10001"), List.of(b("$"), b("{\"id\":10001,\"sku\":\"TN-01\",\"name\":\"Tai nghe Sony Pro\","
                        + "\"categoryId\":5,\"categoryName\":\"Tai nghe\",\"price\":549000.00,\"status\":\"ACTIVE\","
                        + "\"inStock\":true,\"thumbnailUrl\":null,\"extra\":1}")),
                b("product:10002"), List.of(b("$"), b("{\"sku\":\"TN-02\",\"name\":\"Loa\",\"price\":1,"
                        + "\"status\":\"ACTIVE\",\"inStock\":false}")));
        RedisSearchCommand.Page page = RedisSearchCommand.parse(reply, JsonMapper.builder().build());
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(ProductItem::productId).containsExactly(10001L, 10002L);
        ProductItem first = page.items().get(0);
        assertThat(first.price()).isEqualByComparingTo("549000");
        assertThat(first.inStock()).isTrue();
        assertThat(first.categoryName()).isEqualTo("Tai nghe");
        assertThat(page.items().get(1).inStock()).isFalse();
    }

    @Test
    void scanFilterMatchesSameRulesAsQuery() {
        RedisProductDoc doc = new RedisProductDoc(1L, "S", "Tai nghe Sony Pro 37", 5L, "x",
                new BigDecimal("2000000"), "ACTIVE", true, null);
        SearchQuery prefix = new SearchQuery("so pr", List.of("so", "pr"), 5L, new BigDecimal("1000000"),
                new BigDecimal("3000000"), true, SortOrder.RELEVANCE, 1, 24);
        assertThat(RedisProductSearchRepository.matches(prefix, doc)).isTrue();
        SearchQuery inside = new SearchQuery("ony", List.of("ony"), null, null, null, false, SortOrder.RELEVANCE, 1, 24);
        assertThat(RedisProductSearchRepository.matches(inside, doc)).isFalse();
        SearchQuery otherCategory = new SearchQuery(null, List.of(), 6L, null, null, false, SortOrder.PRICE_ASC, 1, 24);
        assertThat(RedisProductSearchRepository.matches(otherCategory, doc)).isFalse();
    }
}
