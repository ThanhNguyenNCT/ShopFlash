package com.shopflash.modules.query.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Tham số đã được controller kiểm tra và điền mặc định. Ngày tính theo +07:00, from và to đều tính trọn ngày.
 * Oracle: created_at >= fromInstant() AND created_at < toExclusiveInstant().
 */
public record ReportQuery(
        Report report,
        LocalDate from,
        LocalDate to,
        JoinHint joinHint,
        RedisMode redisMode,
        int limit,
        int page,
        int size) {

    public static final ZoneOffset ZONE = ZoneOffset.ofHours(7);

    public Instant fromInstant() {
        return from.atStartOfDay().toInstant(ZONE);
    }

    public Instant toExclusiveInstant() {
        return to.plusDays(1).atStartOfDay().toInstant(ZONE);
    }

    /**
     * Số dòng bỏ qua. ReportQueryFactory đã chặn page/size nên kết quả vừa int; nếu vẫn tràn (dựng tay ngoài factory)
     * thì ném lỗi thay vì trả offset âm cho Oracle/Redis.
     */
    public int offset() {
        long offset = Math.multiplyExact((long) Math.max(page - 1, 0), (long) Math.max(size, 0));
        return Math.toIntExact(offset);
    }

    public ReportQuery withJoinHint(JoinHint hint) {
        return new ReportQuery(report, from, to, hint, redisMode, limit, page, size);
    }

    public ReportQuery withRedisMode(RedisMode mode) {
        return new ReportQuery(report, from, to, joinHint, mode, limit, page, size);
    }
}
