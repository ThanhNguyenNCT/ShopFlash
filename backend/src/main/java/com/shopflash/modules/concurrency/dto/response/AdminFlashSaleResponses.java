package com.shopflash.modules.concurrency.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Response của API admin flash sale. Giờ trả theo +07:00. */
public final class AdminFlashSaleResponses {

    private AdminFlashSaleResponses() {
    }

    /** Danh sách phân trang, cùng dạng với module storage. page bắt đầu từ 1. */
    public record Page<T>(List<T> rows, int page, int size, long total) {
    }

    public record Summary(long flashSaleId, String name, String status, OffsetDateTime startAt, OffsetDateTime endAt,
                          int itemCount, long allocatedTotal, long soldTotal) {
    }

    public record Detail(long flashSaleId, String name, String description, String status, OffsetDateTime startAt,
                         OffsetDateTime endAt, long version, Config config, List<Item> items,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record Config(int maxQuantityPerUser, int reservationTimeoutSeconds, boolean allowCancel) {
    }

    public record Item(long itemId, long productId, String sku, String productName, String productStatus,
                       BigDecimal basePrice, BigDecimal salePrice, long allocated, long sold, String status,
                       long onHand, long reservedFlash, long available) {
    }

    public record EligibleProduct(long productId, String sku, String name, String thumbnailUrl, BigDecimal basePrice,
                                  long onHand, long reservedFlash, long available) {
    }

    /**
     * Đối chiếu Oracle với Redis. held = allocated - sold - remaining là số suất đang được giữ (reservation chưa
     * chốt); inSync = false khi Redis thiếu key hoặc held < 0 (Redis báo còn nhiều hơn Oracle cho phép).
     */
    public record Runtime(long flashSaleId, String status, boolean runtimeLoaded, OffsetDateTime serverTime,
                          List<RuntimeItem> items, List<InvariantViolation> invariantViolations) {
    }

    public record RuntimeItem(long itemId, long productId, String productName, long allocated, long sold,
                              Long remaining, Long held, long pendingReservations, boolean inSync) {
    }

    public record InvariantViolation(long productId, Long reservedFlash, long expectedReserved) {
    }

    public record Rebuild(long flashSaleId, int items, long droppedReservations) {
    }
}
