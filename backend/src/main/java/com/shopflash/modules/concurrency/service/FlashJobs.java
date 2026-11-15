package com.shopflash.modules.concurrency.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopflash.modules.concurrency.service.ReservationSweeper.SweepResult;

/**
 * Lịch chạy job của module. Tắt bằng shopflash.flash.jobs.enabled=false (test tự gọi sweeper để kết quả tất định).
 * Redis hoặc Oracle chưa sẵn sàng thì chỉ ghi log, lần sau chạy lại.
 */
@Component
@ConditionalOnProperty(name = "shopflash.flash.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class FlashJobs {

    private static final Logger log = LoggerFactory.getLogger(FlashJobs.class);

    private final ReservationSweeper sweeper;
    private final FlashRuntimeService runtime;
    private final FlashLifecycleService lifecycle;

    public FlashJobs(ReservationSweeper sweeper, FlashRuntimeService runtime, FlashLifecycleService lifecycle) {
        this.sweeper = sweeper;
        this.runtime = runtime;
        this.lifecycle = lifecycle;
    }

    /** SCHEDULED → ACTIVE khi tới giờ; ACTIVE → ENDED sau end_at + thời gian giữ suất + mốc an toàn. */
    @Scheduled(initialDelayString = "${shopflash.flash.sweep-interval-ms:5000}",
            fixedDelayString = "${shopflash.flash.sweep-interval-ms:5000}")
    public void advanceLifecycle() {
        try {
            List<Long> activated = lifecycle.activateDue();
            List<Long> ended = lifecycle.endDue();
            if (!activated.isEmpty() || !ended.isEmpty()) {
                log.info("Flash sale: mở bán {}, kết thúc {}", activated, ended);
            }
        } catch (RuntimeException e) {
            log.warn("Job chuyển trạng thái flash sale lỗi, sẽ chạy lại: {}", e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${shopflash.flash.sweep-interval-ms:5000}",
            fixedDelayString = "${shopflash.flash.sweep-interval-ms:5000}")
    public void sweepReservations() {
        try {
            SweepResult r = sweeper.sweep();
            if (r.released() + r.confirmed() > 0) {
                log.info("Dọn reservation: trả {} suất, chốt {} đơn, {} đang chờ", r.released(), r.confirmed(),
                        r.waiting());
            }
        } catch (RuntimeException e) {
            log.warn("Job dọn reservation lỗi, sẽ chạy lại: {}", e.getMessage());
        }
    }

    /** Nạp lại runtime cho chương trình SCHEDULED/ACTIVE còn thiếu key (Redis khởi động lại, mất dữ liệu). */
    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void reloadMissingRuntime() {
        try {
            int loaded = runtime.ensureLoaded();
            if (loaded > 0) {
                log.info("Đã nạp lại runtime cho {} chương trình flash sale", loaded);
            }
        } catch (RuntimeException e) {
            log.warn("Chưa nạp lại được runtime flash sale: {}", e.getMessage());
        }
    }
}
