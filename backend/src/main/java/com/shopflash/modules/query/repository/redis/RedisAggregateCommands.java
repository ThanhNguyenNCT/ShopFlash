package com.shopflash.modules.query.repository.redis;

import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.COUNTED_STATUS;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.LINE_INDEX;
import static com.shopflash.modules.query.repository.redis.AnalyticsKeys.ORDER_INDEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.shopflash.modules.query.model.ReportQuery;

/**
 * Các lệnh FT.AGGREGATE của từng báo cáo, dạng mảng tham số đúng như gõ ở redis-cli.
 * Mọi lệnh đều có TIMEOUT 0: cấu hình mặc định của Redis Stack là TIMEOUT 500 + ON_TIMEOUT return,
 * nghĩa là quá 500 ms thì trả kết quả dở dang mà không báo lỗi.
 */
final class RedisAggregateCommands {

    static final String MONTH_EXPR = "timefmt(@createdAt+25200,\"%Y-%m\")";
    static final int MAX_ROWS = 10000;

    private RedisAggregateCommands() {
    }

    /** Đơn được tính trong khoảng [from, toExclusive), mốc theo +07:00 đổi ra epoch giây. */
    static String filter(ReportQuery q) {
        return COUNTED_STATUS + " @createdAt:[" + q.fromInstant().getEpochSecond()
                + " (" + q.toExclusiveInstant().getEpochSecond() + "]";
    }

    static String[] revenueByMonth(ReportQuery q) {
        return args("FT.AGGREGATE", ORDER_INDEX, filter(q), "TIMEOUT", "0",
                "APPLY", MONTH_EXPR, "AS", "month",
                "GROUPBY", "1", "@month",
                "REDUCE", "COUNT", "0", "AS", "orderCount",
                "REDUCE", "SUM", "1", "@totalAmount", "AS", "revenue",
                "SORTBY", "2", "@month", "ASC",
                "LIMIT", "0", String.valueOf(MAX_ROWS));
    }

    static String[] revenueByCategory(ReportQuery q) {
        return args("FT.AGGREGATE", LINE_INDEX, filter(q), "TIMEOUT", "0",
                "LOAD", "3", "$.categoryName", "AS", "categoryName",
                "GROUPBY", "1", "@categoryId",
                "REDUCE", "SUM", "1", "@quantity", "AS", "quantity",
                "REDUCE", "SUM", "1", "@lineTotal", "AS", "revenue",
                "REDUCE", "FIRST_VALUE", "1", "@categoryName", "AS", "categoryName",
                "SORTBY", "4", "@revenue", "DESC", "@categoryId", "ASC",
                "LIMIT", "0", String.valueOf(MAX_ROWS));
    }

    static String[] topProducts(ReportQuery q) {
        return args("FT.AGGREGATE", LINE_INDEX, filter(q), "TIMEOUT", "0",
                "LOAD", "6", "$.productName", "AS", "productName", "$.categoryName", "AS", "categoryName",
                "GROUPBY", "1", "@productId",
                "REDUCE", "SUM", "1", "@quantity", "AS", "quantity",
                "REDUCE", "SUM", "1", "@lineTotal", "AS", "revenue",
                "REDUCE", "FIRST_VALUE", "1", "@productName", "AS", "productName",
                "REDUCE", "FIRST_VALUE", "1", "@categoryName", "AS", "categoryName",
                "SORTBY", "4", "@quantity", "DESC", "@productId", "ASC", "MAX", String.valueOf(q.limit()));
    }

    /** VIP lượt 1: tổng chi theo khách, rồi trung bình trên các khách và số khách. */
    static String[] vipAverage(ReportQuery q) {
        return args("FT.AGGREGATE", ORDER_INDEX, filter(q), "TIMEOUT", "0",
                "GROUPBY", "1", "@userId",
                "REDUCE", "SUM", "1", "@totalAmount", "AS", "totalSpent",
                "GROUPBY", "0",
                "REDUCE", "AVG", "1", "@totalSpent", "AS", "averageSpent",
                "REDUCE", "COUNT", "0", "AS", "customerCount");
    }

    /** VIP lượt 2: khách có tổng chi lớn hơn trung bình (hằng số lấy từ lượt 1), một trang. */
    static String[] vipPage(ReportQuery q, String average) {
        return args("FT.AGGREGATE", ORDER_INDEX, filter(q), "TIMEOUT", "0",
                "LOAD", "6", "$.userName", "AS", "userName", "$.userEmail", "AS", "userEmail",
                "GROUPBY", "1", "@userId",
                "REDUCE", "SUM", "1", "@totalAmount", "AS", "totalSpent",
                "REDUCE", "COUNT", "0", "AS", "orderCount",
                "REDUCE", "FIRST_VALUE", "1", "@userName", "AS", "fullName",
                "REDUCE", "FIRST_VALUE", "1", "@userEmail", "AS", "email",
                "FILTER", "@totalSpent > " + average,
                "SORTBY", "4", "@totalSpent", "DESC", "@userId", "ASC",
                "LIMIT", String.valueOf(q.offset()), String.valueOf(q.size()));
    }

    /** VIP lượt 2 (cùng pipeline): tổng số khách VIP cho page.total. */
    static String[] vipCount(ReportQuery q, String average) {
        return args("FT.AGGREGATE", ORDER_INDEX, filter(q), "TIMEOUT", "0",
                "GROUPBY", "1", "@userId",
                "REDUCE", "SUM", "1", "@totalAmount", "AS", "totalSpent",
                "FILTER", "@totalSpent > " + average,
                "GROUPBY", "0",
                "REDUCE", "COUNT", "0", "AS", "vipCount");
    }

    /** FT.AGGREGATE idx q ... -> FT.PROFILE idx AGGREGATE QUERY q ... */
    static String[] profile(String[] aggregate) {
        List<String> out = new ArrayList<>(aggregate.length + 3);
        out.add("FT.PROFILE");
        out.add(aggregate[1]);
        out.add("AGGREGATE");
        out.add("QUERY");
        out.addAll(Arrays.asList(aggregate).subList(2, aggregate.length));
        return out.toArray(String[]::new);
    }

    static String[] args(String... a) {
        return a;
    }
}
