package com.shopflash.modules.concurrency.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lúc khởi động, nạp runtime cho chương trình SCHEDULED/ACTIVE nào còn thiếu trên Redis.
 * Redis hoặc Oracle chưa sẵn sàng thì chỉ ghi log; job định kỳ sẽ nạp lại sau.
 */
@Component
public class FlashRuntimeBootstrap {

    private static final Logger log = LoggerFactory.getLogger(FlashRuntimeBootstrap.class);

    private final FlashRuntimeService runtime;

    public FlashRuntimeBootstrap(FlashRuntimeService runtime) {
        this.runtime = runtime;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadMissingRuntime() {
        try {
            int loaded = runtime.ensureLoaded();
            if (loaded > 0) {
                log.info("Đã nạp runtime cho {} chương trình flash sale", loaded);
            }
        } catch (RuntimeException e) {
            log.warn("Chưa nạp được runtime flash sale lúc khởi động: {}", e.getMessage());
        }
    }
}
