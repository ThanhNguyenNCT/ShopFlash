package com.shopflash.modules.indexing.service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.indexing.model.Keywords;
import com.shopflash.modules.indexing.model.OracleVariant;
import com.shopflash.modules.indexing.model.RedisVariant;
import com.shopflash.modules.indexing.model.SearchQuery;
import com.shopflash.modules.indexing.model.SearchSource;
import com.shopflash.modules.indexing.model.SortOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Kiểm tra tham số tìm kiếm (nhận dạng chuỗi từ controller) và dựng SearchQuery.
 * Mọi lỗi là 400 VALIDATION_ERROR kèm tên trường, thông báo tiếng Việt.
 */
@Component
public class SearchQueryFactory {

    public static final int MAX_Q_LENGTH = 100;
    public static final int DEFAULT_SIZE = 24;
    public static final int MAX_SIZE = 48;
    /** RediSearch không trả quá MAXSEARCHRESULTS = 10.000 kết quả; áp dụng cho mọi nguồn để hai nguồn như nhau. */
    public static final int MAX_RESULTS = 10_000;
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999999.99");

    public SearchQuery fromParams(String q, String categoryId, String minPrice, String maxPrice, String inStock,
            String sort, String page, String size) {
        String text = blankToNull(q);
        if (text != null && text.length() > MAX_Q_LENGTH) {
            throw ApiException.badRequest("q", "Từ khóa tối đa " + MAX_Q_LENGTH + " ký tự");
        }
        List<String> keywords = Keywords.parse(text);
        Long category = positiveLong("categoryId", categoryId);
        BigDecimal min = price("minPrice", minPrice);
        BigDecimal max = price("maxPrice", maxPrice);
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw ApiException.badRequest("minPrice", "minPrice phải nhỏ hơn hoặc bằng maxPrice");
        }
        boolean onlyInStock = bool("inStock", inStock);
        SortOrder order = sort(sort, !keywords.isEmpty());
        int p = intInRange("page", page, 1, 1, MAX_RESULTS);
        int s = intInRange("size", size, DEFAULT_SIZE, 1, MAX_SIZE);
        if ((long) p * s > MAX_RESULTS) {
            throw ApiException.badRequest("page", "Chỉ xem được " + MAX_RESULTS
                    + " kết quả đầu tiên, hãy thu hẹp bộ lọc");
        }
        return new SearchQuery(text, keywords, category, min, max, onlyInStock, order, p, s);
    }

    /** Nguồn cho trang Lab: rỗng hoặc "auto" = định tuyến như API công khai. */
    public Optional<SearchSource> source(String value) {
        String v = blankToNull(value);
        if (v == null || v.equalsIgnoreCase("auto")) {
            return Optional.empty();
        }
        for (SearchSource s : SearchSource.values()) {
            if (s.slug().equalsIgnoreCase(v)) {
                return Optional.of(s);
            }
        }
        throw ApiException.badRequest("source", "source phải là auto, oracle hoặc redis");
    }

    public OracleVariant oracleVariant(String value) {
        return enumValue("oracleVariant", value, OracleVariant.class, OracleVariant.AUTO, "AUTO, LIKE hoặc FULL");
    }

    public RedisVariant redisVariant(String value) {
        return enumValue("redisVariant", value, RedisVariant.class, RedisVariant.SEARCH, "SEARCH hoặc SCAN");
    }

    private static SortOrder sort(String value, boolean hasKeywords) {
        String v = blankToNull(value);
        if (v == null) {
            return hasKeywords ? SortOrder.RELEVANCE : SortOrder.PRICE_ASC;
        }
        SortOrder order = SortOrder.fromSlug(v).orElseThrow(() ->
                ApiException.badRequest("sort", "sort phải là relevance, price_asc hoặc price_desc"));
        if (order == SortOrder.RELEVANCE && !hasKeywords) {
            throw ApiException.badRequest("sort", "Chỉ xếp theo độ liên quan khi có từ khóa");
        }
        return order;
    }

    private static <E extends Enum<E>> E enumValue(String field, String value, Class<E> type, E fallback,
            String allowed) {
        String v = blankToNull(value);
        if (v == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(field, field + " phải là " + allowed);
        }
    }

    private static Long positiveLong(String field, String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            long n = Long.parseLong(v);
            if (n <= 0) {
                throw ApiException.badRequest(field, field + " phải là số nguyên dương");
            }
            return n;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(field, field + " phải là số nguyên dương");
        }
    }

    private static BigDecimal price(String field, String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            BigDecimal n = new BigDecimal(v);
            if (n.signum() < 0) {
                throw ApiException.badRequest(field, field + " phải lớn hơn hoặc bằng 0");
            }
            if (n.compareTo(MAX_PRICE) > 0) {
                throw ApiException.badRequest(field, field + " quá lớn");
            }
            return n;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(field, field + " phải là số");
        }
    }

    private static boolean bool(String field, String value) {
        String v = blankToNull(value);
        if (v == null) {
            return false;
        }
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        throw ApiException.badRequest(field, field + " phải là true hoặc false");
    }

    private static int intInRange(String field, String value, int fallback, int min, int max) {
        String v = blankToNull(value);
        if (v == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                throw ApiException.badRequest(field, field + " phải từ " + min + " đến " + max);
            }
            return n;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(field, field + " phải từ " + min + " đến " + max);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
