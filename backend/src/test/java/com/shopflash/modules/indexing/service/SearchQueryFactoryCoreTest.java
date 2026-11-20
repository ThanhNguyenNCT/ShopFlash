package com.shopflash.modules.indexing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.indexing.model.Route;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SortOrder;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SearchQueryFactoryCoreTest {

    final SearchQueryFactory factory = new SearchQueryFactory();

    SearchQuery parse(String q, String categoryId, String minPrice, String maxPrice, String inStock, String sort,
            String page, String size) {
        return factory.fromParams(q, categoryId, minPrice, maxPrice, inStock, sort, page, size);
    }

    static void assertField(Throwable e, String field) {
        assertThat(e).isInstanceOf(ApiException.class);
        ApiException api = (ApiException) e;
        assertThat(api.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(api.details()).containsEntry("field", field);
    }

    @Test
    void defaultsWithoutKeywordBrowseByPrice() {
        SearchQuery q = parse(null, null, null, null, null, null, null, null);
        assertThat(q.route()).isEqualTo(Route.BROWSE);
        assertThat(q.sort()).isEqualTo(SortOrder.PRICE_ASC);
        assertThat(q.page()).isEqualTo(1);
        assertThat(q.size()).isEqualTo(24);
        assertThat(q.offset()).isZero();
        assertThat(q.inStock()).isFalse();
    }

    @Test
    void keywordDefaultsToRelevance() {
        SearchQuery q = parse("Tai nghe", "5", "1000000", "5000000", "true", null, "2", "12");
        assertThat(q.route()).isEqualTo(Route.KEYWORD);
        assertThat(q.keywords()).containsExactly("tai", "nghe");
        assertThat(q.sort()).isEqualTo(SortOrder.RELEVANCE);
        assertThat(q.categoryId()).isEqualTo(5L);
        assertThat(q.minPrice()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(q.inStock()).isTrue();
        assertThat(q.offset()).isEqualTo(12);
    }

    @Test
    void queryWithOnlyStopWordsIsBrowse() {
        SearchQuery q = parse("a the", null, null, null, null, null, null, null);
        assertThat(q.hasKeywords()).isFalse();
        assertThat(q.sort()).isEqualTo(SortOrder.PRICE_ASC);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> parse(null, null, "5", "1", null, null, null, null))
                .satisfies(e -> assertField(e, "minPrice"));
        assertThatThrownBy(() -> parse(null, null, "-1", null, null, null, null, null))
                .satisfies(e -> assertField(e, "minPrice"));
        assertThatThrownBy(() -> parse(null, "0", null, null, null, null, null, null))
                .satisfies(e -> assertField(e, "categoryId"));
        assertThatThrownBy(() -> parse(null, "abc", null, null, null, null, null, null))
                .satisfies(e -> assertField(e, "categoryId"));
        assertThatThrownBy(() -> parse(null, null, null, null, "yes", null, null, null))
                .satisfies(e -> assertField(e, "inStock"));
        assertThatThrownBy(() -> parse(null, null, null, null, null, "newest", null, null))
                .satisfies(e -> assertField(e, "sort"));
        assertThatThrownBy(() -> parse(null, null, null, null, null, "relevance", null, null))
                .satisfies(e -> assertField(e, "sort"));
        assertThatThrownBy(() -> parse(null, null, null, null, null, null, null, "49"))
                .satisfies(e -> assertField(e, "size"));
        assertThatThrownBy(() -> parse(null, null, null, null, null, null, "418", "24"))
                .satisfies(e -> assertField(e, "page"));
        assertThatThrownBy(() -> parse("x".repeat(101), null, null, null, null, null, null, null))
                .satisfies(e -> assertField(e, "q"));
    }
}
