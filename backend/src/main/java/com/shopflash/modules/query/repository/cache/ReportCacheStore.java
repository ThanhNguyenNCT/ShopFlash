package com.shopflash.modules.query.repository.cache;

import java.time.Duration;
import java.util.Optional;

/** Nơi giữ kết quả báo cáo đã tính (Redis). Tách interface để test không cần Redis. */
public interface ReportCacheStore {

    Optional<String> get(String key);

    /** Ghi đè giá trị, hết hạn sau ttl. */
    void put(String key, String value, Duration ttl);
}
