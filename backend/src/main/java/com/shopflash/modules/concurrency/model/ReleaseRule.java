package com.shopflash.modules.concurrency.model;

/**
 * Khi nào được trả suất của một reservation (tham số của release.lua).
 *
 * @param allowedStatuses trạng thái được phép trả, cách nhau bởi dấu phẩy
 * @param graceMs -1 là không xét giờ; >= 0 thì chỉ trả khi now >= expiresAt + graceMs (đồng hồ Redis)
 * @param ownerUserId null là không kiểm tra chủ
 */
public record ReleaseRule(String allowedStatuses, long graceMs, Long ownerUserId) {

    /** Job dọn: PENDING đã quá hạn. */
    public static ReleaseRule expiredPending() {
        return new ReleaseRule("PENDING", 0, null);
    }

    /** Job dọn: CLAIMED quá expiresAt + grace mà không có đơn trong Oracle (lần đặt đơn bị sập giữa chừng). */
    public static ReleaseRule staleClaim(long graceMs) {
        return new ReleaseRule("CLAIMED", graceMs, null);
    }

    /** Transaction đặt đơn rollback. */
    public static ReleaseRule rollback() {
        return new ReleaseRule("CLAIMED", -1, null);
    }

    /** Khách tự hủy giữ suất. */
    public static ReleaseRule cancelledBy(long userId) {
        return new ReleaseRule("PENDING", -1, userId);
    }
}
