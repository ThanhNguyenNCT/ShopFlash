package com.shopflash.modules.concurrency.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shopflash.modules.concurrency.model.ReleaseRule;
import com.shopflash.modules.concurrency.model.ReservationView;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;

/**
 * Dọn reservation hết hạn (AGENTS.md, "Job dọn reservation"). Chạy trùng trên nhiều instance cũng được: release.lua
 * và confirm.lua kiểm tra lại trạng thái và giờ ngay trong Redis trước khi đổi.
 * <ul>
 * <li>PENDING đã hết hạn: trả suất.</li>
 * <li>CLAIMED quá expiresAt + claimGrace (lần đặt đơn bị sập giữa chừng): có đơn trong Oracle thì confirm, không có
 * thì trả suất. claimGrace (30 s) phải dài hơn hẳn timeout transaction đặt đơn (10 s).</li>
 * </ul>
 */
@Service
public class ReservationSweeper {

    static final int BATCH = 500;

    public record SweepResult(int released, int confirmed, int waiting, int forgotten) {
    }

    private final FlashRuntimeRepository runtime;
    private final FlashOrderRepository orders;
    private final long claimGraceMs;

    public ReservationSweeper(FlashRuntimeRepository runtime, FlashOrderRepository orders,
            @Value("${shopflash.flash.claim-grace-ms:30000}") long claimGraceMs) {
        this.runtime = runtime;
        this.orders = orders;
        this.claimGraceMs = claimGraceMs;
    }

    public SweepResult sweep() {
        long now = runtime.serverTimeMs();
        List<String> due = runtime.dueReservations(now, BATCH);
        int released = 0;
        int confirmed = 0;
        int waiting = 0;
        int forgotten = 0;
        for (String id : due) {
            Optional<ReservationView> found = runtime.find(id);
            if (found.isEmpty()) {
                runtime.forgetDue(id);
                forgotten++;
                continue;
            }
            ReservationView view = found.get();
            switch (view.status()) {
                case "PENDING" -> {
                    if (runtime.release(id, ReleaseRule.expiredPending())) {
                        released++;
                    }
                }
                case "CLAIMED" -> {
                    if (now < view.expiresAtMs() + claimGraceMs) {
                        waiting++;
                    } else if (orders.findByIdempotencyKey(FlashCheckoutService.idempotencyKey(id)).isPresent()) {
                        if (runtime.confirm(id, view.itemId())) {
                            confirmed++;
                        }
                    } else if (runtime.release(id, ReleaseRule.staleClaim(claimGraceMs))) {
                        released++;
                    }
                }
                default -> {
                    runtime.forgetDue(id);
                    forgotten++;
                }
            }
        }
        return new SweepResult(released, confirmed, waiting, forgotten);
    }
}
