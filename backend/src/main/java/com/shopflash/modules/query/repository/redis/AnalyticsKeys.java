package com.shopflash.modules.query.repository.redis;

/** Key, prefix và index của read model analytics (tạo bởi db/redis/init.lua, module này không tạo index). */
public final class AnalyticsKeys {

    public static final String ORDER_INDEX = "idx:order-analytics";
    public static final String LINE_INDEX = "idx:order-line-analytics";
    public static final String ORDER_PREFIX = "order:analytics:";
    public static final String LINE_PREFIX = "order:line:";
    public static final String META = "analytics:meta";

    /** Đơn được tính (CONFIRMED|COMPLETED) dạng truy vấn RediSearch. */
    public static final String COUNTED_STATUS = "@status:{CONFIRMED|COMPLETED}";

    private AnalyticsKeys() {
    }
}
