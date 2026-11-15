package com.shopflash.modules.concurrency.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.shopflash.modules.concurrency.dto.response.ReservationResponse;
import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.model.ReleaseRule;
import com.shopflash.modules.concurrency.model.ReservationView;
import com.shopflash.modules.concurrency.model.ReserveOutcome;
import com.shopflash.modules.concurrency.repository.oracle.FlashCatalogQueries;
import com.shopflash.modules.concurrency.repository.oracle.FlashCatalogQueries.ItemRow;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;
import com.shopflash.modules.concurrency.security.CurrentCustomer;

/**
 * Giữ suất, xem và hủy suất đã giữ. Giữ suất chỉ chạy trên Redis (reserve.lua); chỉ lượt giữ được mới đọc Oracle
 * để lấy tên và giá sản phẩm, nên lượt hết suất không tốn truy vấn Oracle nào.
 */
@Service
public class ReservationService {

    private static final Pattern RESERVATION_ID = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");

    public record Reserved(boolean created, ReservationResponse reservation) {
    }

    private final FlashRuntimeRepository runtime;
    private final FlashCatalogQueries catalog;
    private final CurrentCustomer customer;

    public ReservationService(FlashRuntimeRepository runtime, FlashCatalogQueries catalog, CurrentCustomer customer) {
        this.runtime = runtime;
        this.catalog = catalog;
        this.customer = customer;
    }

    /** created = false khi người dùng đã có suất đang chờ cho sản phẩm này (bấm hai lần). */
    public Reserved reserve(long saleId, long itemId, int quantity) {
        long userId = customer.requireCustomerId();
        ReserveOutcome outcome = runtime.reserve(saleId, itemId, userId, quantity);
        ReservationView view = runtime.find(outcome.reservationId()).orElseThrow(FlashErrors::reservationNotFound);
        return new Reserved(outcome.created(), response(view, outcome.serverTimeMs()));
    }

    public ReservationResponse get(String reservationId) {
        ReservationView view = owned(reservationId, customer.requireCustomerId());
        return response(view, runtime.serverTimeMs());
    }

    /** Hủy suất đang PENDING. Đã trả rồi (hết hạn, hủy trước đó) thì coi như xong; đang hoặc đã đặt đơn thì 409. */
    public void cancel(String reservationId) {
        long userId = customer.requireCustomerId();
        ReservationView view = owned(reservationId, userId);
        if ("RELEASED".equals(view.status())) {
            return;
        }
        if (!"PENDING".equals(view.status()) || !runtime.release(reservationId, ReleaseRule.cancelledBy(userId))) {
            // release.lua từ chối: trạng thái vừa đổi (job đã trả, hoặc người dùng vừa bấm đặt đơn).
            String now = runtime.find(reservationId).map(ReservationView::status).orElse("RELEASED");
            if (!"RELEASED".equals(now)) {
                throw FlashErrors.reservationAlreadyUsed();
            }
        }
    }

    /** Reservation của người dùng này; không có hoặc của người khác thì 404 (không lộ là có tồn tại). */
    ReservationView owned(String reservationId, long userId) {
        requireId(reservationId);
        ReservationView view = runtime.find(reservationId).orElseThrow(FlashErrors::reservationNotFound);
        if (view.userId() != userId) {
            throw FlashErrors.reservationNotFound();
        }
        return view;
    }

    static void requireId(String reservationId) {
        if (reservationId == null || !RESERVATION_ID.matcher(reservationId).matches()) {
            throw FlashErrors.reservationNotFound();
        }
    }

    private ReservationResponse response(ReservationView view, long serverTimeMs) {
        ItemRow item = catalog.item(view.itemId()).orElseThrow(FlashErrors::itemNotFound);
        BigDecimal total = item.salePrice().multiply(BigDecimal.valueOf(view.quantity()));
        return new ReservationResponse(view.reservationId(), view.saleId(), view.itemId(), view.productId(),
                item.productName(), item.thumbnailUrl(), view.quantity(), item.salePrice(), total, view.status(),
                FlashTime.ofEpochMs(view.expiresAtMs()), FlashTime.ofEpochMs(serverTimeMs));
    }
}
