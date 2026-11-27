package com.shopflash.modules.concurrency.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.dto.request.FlashCheckoutRequest;
import com.shopflash.modules.concurrency.dto.response.FlashOrderResponse;
import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.model.ClaimResult;
import com.shopflash.modules.concurrency.model.ClaimedReservation;
import com.shopflash.modules.concurrency.model.ReleaseRule;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository.NewOrder;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository.OrderLine;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository.StoredOrder;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;
import com.shopflash.modules.concurrency.security.CurrentCustomer;
import com.shopflash.modules.storage.service.ProductProjectionSync;

/**
 * Đặt đơn flash sale theo kiểu Try–Confirm–Cancel giữa Redis và Oracle (AGENTS.md, "Luồng mua flash sale", bước 2):
 * <ol>
 * <li>Try: claim.lua chuyển reservation PENDING → CLAIMED (job dọn không trả suất này nữa trong lúc ghi đơn).</li>
 * <li>Một transaction Oracle: tăng sold có điều kiện, xuất kho phần đã giữ, ghi orders và order_items.</li>
 * <li>Sau commit: confirm.lua (CONFIRMED). Sau rollback: release.lua trả suất về Redis (Cancel, bù trừ).</li>
 * </ol>
 * Hook được đăng ký trong transaction trước câu ghi đầu tiên. Không biết commit hay chưa (STATUS_UNKNOWN), hoặc
 * Redis lỗi lúc confirm/release, thì để nguyên CLAIMED: job dọn tra orders theo idempotency_key rồi chốt sau.
 */
@Service
public class FlashCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(FlashCheckoutService.class);

    /** Job chỉ xử lý CLAIMED quá expiresAt + 30 s; transaction phải ngắn hơn nhiều. */
    static final int TRANSACTION_TIMEOUT_SECONDS = 10;

    /**
     * Từ lúc claim tới lúc transaction có kết nối không được quá mốc này. Pool Oracle cạn thì chờ kết nối tới 30 s
     * (Hikari mặc định) trước khi timeout 10 s của transaction bắt đầu tính; không chặn thì đơn có thể commit sau khi
     * job dọn đã trả suất. Với mốc này, từ lúc claim tới commit tối đa khoảng 15 s, dưới mốc 30 s của job.
     */
    static final long MAX_START_DELAY_NANOS = 5_000_000_000L;

    public record Placed(boolean created, FlashOrderResponse order) {
    }

    private record Recipient(String name, String phone, String address) {
    }

    private final FlashRuntimeRepository runtime;
    private final FlashOrderRepository orders;
    private final CurrentCustomer customer;
    private final TransactionTemplate transaction;
    private final ProductProjectionSync projections;

    public FlashCheckoutService(FlashRuntimeRepository runtime, FlashOrderRepository orders,
            CurrentCustomer customer, PlatformTransactionManager transactions, ProductProjectionSync projections) {
        this.runtime = runtime;
        this.orders = orders;
        this.customer = customer;
        this.projections = projections;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
    }

    public static String idempotencyKey(String reservationId) {
        return "FS-" + reservationId;
    }

    public Placed checkout(String reservationId, FlashCheckoutRequest request) {
        long userId = customer.requireCustomerId();
        ReservationService.requireId(reservationId);
        Recipient recipient = recipient(request);
        String key = idempotencyKey(reservationId);

        long claimStarted = System.nanoTime();
        ClaimResult claim;
        try {
            claim = runtime.claim(reservationId, userId);
        } catch (ApiException e) {
            // Đang CLAIMED: có thể lần trước đã commit nhưng confirm chưa chạy được. Có đơn thì trả lại đơn đó.
            if ("RESERVATION_ALREADY_USED".equals(e.code())) {
                return existing(key, userId).map(o -> new Placed(false, o)).orElseThrow(() -> e);
            }
            throw e;
        }
        if (claim.alreadyConfirmed()) {
            return new Placed(false, existing(key, userId).orElseThrow(FlashErrors::reservationNotFound));
        }

        ClaimedReservation claimed = claim.reservation();
        AtomicBoolean hooked = new AtomicBoolean(false);
        try {
            FlashOrderResponse order = transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new ReservationOutcome(claimed));
                hooked.set(true);
                if (startedTooLate(claimStarted, System.nanoTime())) {
                    // Hook rollback trả suất ngay; khách giữ suất lại.
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHECKOUT_BUSY",
                            "Hệ thống đang quá tải nên chưa đặt được đơn. Suất đã được trả lại, vui lòng giữ suất lại");
                }
                return writeOrder(claimed, recipient, key);
            });
            return new Placed(true, order);
        } catch (DuplicateKeyException e) {
            // uk_orders_idempotency: đã có đơn cho reservation này (hook đã trả suất của lần ghi trùng).
            throw FlashErrors.reservationAlreadyUsed();
        } catch (RuntimeException e) {
            if (!hooked.get()) {
                // Transaction chưa bắt đầu (ví dụ hết kết nối trong pool): không có hook, trả suất ngay.
                releaseQuietly(claimed.reservationId());
            }
            throw e;
        }
    }

    static boolean startedTooLate(long claimNanos, long nowNanos) {
        return nowNanos - claimNanos > MAX_START_DELAY_NANOS;
    }

    /** Thân transaction. Khóa FLASH_SALE_ITEMS rồi PRODUCT_INVENTORY (thứ tự khóa của module). */
    private FlashOrderResponse writeOrder(ClaimedReservation r, Recipient recipient, String key) {
        if (!orders.addSold(r.itemId(), r.quantity())) {
            throw FlashErrors.soldOut();
        }
        OrderLine line = orders.orderLine(r.itemId()).orElseThrow(FlashErrors::itemNotFound);
        if (!orders.takeFlashStock(line.productId(), r.quantity())) {
            log.error("Tồn kho flash của sản phẩm {} không đủ {} khi đặt đơn reservation {}: bất biến đã lệch",
                    line.productId(), r.quantity(), r.reservationId());
            throw FlashErrors.soldOut();
        }
        // Đăng ký sau ReservationOutcome nên chạy sau confirm.lua; rollback thì không ghi Redis.
        projections.afterCommit(List.of(line.productId()));
        BigDecimal total = line.unitPrice().multiply(BigDecimal.valueOf(r.quantity()));
        String orderCode = "FS-" + UUID.randomUUID().toString().replace("-", "");
        long orderId = orders.insertOrder(new NewOrder(orderCode, key, r.userId(), total, recipient.name(),
                recipient.phone(), recipient.address()));
        orders.insertOrderItem(orderId, r.itemId(), line, r.quantity());
        return new FlashOrderResponse(orderId, orderCode, "PENDING", total);
    }

    private Optional<FlashOrderResponse> existing(String key, long userId) {
        return orders.findByIdempotencyKey(key)
                .filter(o -> o.userId() == userId)
                .map(FlashCheckoutService::response);
    }

    private static FlashOrderResponse response(StoredOrder o) {
        return new FlashOrderResponse(o.orderId(), o.orderCode(), o.status(), o.totalAmount());
    }

    private void releaseQuietly(String reservationId) {
        try {
            runtime.release(reservationId, ReleaseRule.rollback());
        } catch (RuntimeException e) {
            log.warn("Chưa trả được suất {} sau lỗi đặt đơn, job dọn sẽ trả sau: {}", reservationId, e.getMessage());
        }
    }

    private static Recipient recipient(FlashCheckoutRequest request) {
        if (request == null) {
            throw ApiException.badRequest("body", "Thiếu thông tin nhận hàng");
        }
        return new Recipient(text(request.recipientName(), "recipientName", 150),
                text(request.recipientPhone(), "recipientPhone", 30),
                text(request.shippingAddress(), "shippingAddress", 500));
    }

    /** Giống CheckoutNormalizer của transaction: cột VARCHAR2 giới hạn theo byte. */
    private static String text(String raw, String field, int maxBytes) {
        if (raw == null) {
            throw ApiException.badRequest(field, "Thông tin nhận hàng không được trống");
        }
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw ApiException.badRequest(field, "Thông tin nhận hàng không hợp lệ");
        }
        return value;
    }

    /** Chốt reservation theo kết quả transaction Oracle. Lỗi ở đây chỉ ghi log; job dọn xử lý lại. */
    private final class ReservationOutcome implements TransactionSynchronization {

        private final ClaimedReservation reservation;

        ReservationOutcome(ClaimedReservation reservation) {
            this.reservation = reservation;
        }

        @Override
        public void afterCompletion(int status) {
            String id = reservation.reservationId();
            try {
                if (status == STATUS_COMMITTED) {
                    if (!runtime.confirm(id, reservation.itemId())) {
                        log.warn("Đơn của reservation {} đã commit nhưng reservation không còn CLAIMED", id);
                    }
                } else if (status == STATUS_ROLLED_BACK) {
                    runtime.release(id, ReleaseRule.rollback());
                } else {
                    log.warn("Không rõ transaction đặt đơn của reservation {} đã commit chưa; job dọn sẽ tra Oracle",
                            id);
                }
            } catch (RuntimeException e) {
                log.warn("Chưa chốt được reservation {} trên Redis, job dọn sẽ xử lý: {}", id, e.getMessage());
            }
        }
    }
}
