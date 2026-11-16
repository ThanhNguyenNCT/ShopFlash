package com.shopflash.modules.query.service;

import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Đọc tham số dạng chuỗi từ request, kiểm tra và điền mặc định theo contract trong AGENTS.md.
 * Mọi lỗi là ApiException VALIDATION_ERROR có details.field.
 */
@Component
public class ReportQueryFactory {

    public static final int DEFAULT_LIMIT = 10;
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    /** Chặn trên của page: (page - 1) * size luôn vừa int, không bao giờ âm. */
    public static final int MAX_PAGE = 100_000;
    /** Khoảng năm hợp lệ cho from/to, tránh năm âm hoặc quá xa làm tràn epoch trên Oracle/Redis. */
    public static final int MIN_YEAR = 2000;
    public static final int MAX_YEAR = 2100;

    private final Clock clock;

    @Autowired
    public ReportQueryFactory() {
        this(Clock.system(ReportQuery.ZONE));
    }

    public ReportQueryFactory(Clock clock) {
        this.clock = clock;
    }

    /** Hôm nay theo +07:00. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(ReportQuery.ZONE));
    }

    /** Mặc định from: ngày đầu tháng của 11 tháng trước (tức 12 tháng gần nhất tính cả tháng này). */
    public LocalDate defaultFrom() {
        return today().minusMonths(11).withDayOfMonth(1);
    }

    public Report report(String slug) {
        Report report = Report.fromSlug(slug);
        if (report == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Báo cáo '" + slug + "' không tồn tại",
                    Map.of("field", "report",
                            "allowed", Arrays.stream(Report.values()).map(Report::slug).toList()),
                    null);
        }
        return report;
    }

    public Source source(String value) {
        if (isBlank(value)) {
            return Source.ORACLE;
        }
        try {
            return Source.from(value);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("source", "source phải là oracle, redis hoặc cache");
        }
    }

    public JoinHint joinHint(String value) {
        return parseEnum(JoinHint.class, value, JoinHint.AUTO, "joinHint",
                "joinHint phải là AUTO, NL, HASH hoặc MERGE");
    }

    public RedisMode redisMode(String value) {
        return parseEnum(RedisMode.class, value, RedisMode.AGGREGATE, "redisMode",
                "redisMode phải là AGGREGATE hoặc CLIENT");
    }

    /** Dựng ReportQuery từ tham số query string (GET báo cáo, compare, plan). */
    public ReportQuery fromParams(Report report, String from, String to, String joinHint, String redisMode,
            String limit, String page, String size) {
        LocalDate fromDate = parseDate("from", from);
        LocalDate toDate = parseDate("to", to);
        return build(report, fromDate, toDate, joinHint(joinHint), redisMode(redisMode),
                parseInt("limit", limit, DEFAULT_LIMIT, 1, 100, "limit phải từ 1 đến 100"),
                parseInt("page", page, DEFAULT_PAGE, 1, MAX_PAGE, "page phải từ 1 đến " + MAX_PAGE),
                parseInt("size", size, DEFAULT_SIZE, 1, 100, "size phải từ 1 đến 100"));
    }

    /** Điền mặc định cho ngày và kiểm tra from <= to. Các giá trị khác phải đã hợp lệ. */
    public ReportQuery build(Report report, LocalDate from, LocalDate to, JoinHint joinHint, RedisMode redisMode,
            int limit, int page, int size) {
        checkYear("from", from);
        checkYear("to", to);
        LocalDate toDate = to != null ? to : today();
        LocalDate fromDate = from != null ? from : defaultFrom();
        if (fromDate.isAfter(toDate)) {
            throw ApiException.validation("from", "from không được sau to");
        }
        return new ReportQuery(report, fromDate, toDate, joinHint, redisMode, limit, page, size);
    }

    private static LocalDate parseDate(String field, String value) {
        if (isBlank(value)) {
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.validation(field, field + " phải là ngày hợp lệ dạng YYYY-MM-DD");
        }
        checkYear(field, date);
        return date;
    }

    /** Năm của from/to phải trong MIN_YEAR..MAX_YEAR (cả body benchmark lẫn query string). */
    static void checkYear(String field, LocalDate date) {
        if (date != null && (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR)) {
            throw ApiException.validation(field, field + " phải trong khoảng năm " + MIN_YEAR + " đến " + MAX_YEAR);
        }
    }

    private static int parseInt(String field, String value, int defaultValue, int min, int max, String message) {
        if (isBlank(value)) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw ApiException.validation(field, message);
        }
        if (parsed < min || parsed > max) {
            throw ApiException.validation(field, message);
        }
        return parsed;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E defaultValue, String field,
            String message) {
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.validation(field, message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
