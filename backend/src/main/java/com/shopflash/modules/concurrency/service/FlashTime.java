package com.shopflash.modules.concurrency.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** API trả giờ ISO-8601 có offset +07:00; Redis lưu epoch mili giây. */
public final class FlashTime {

    public static final ZoneOffset VN = ZoneOffset.ofHours(7);

    private FlashTime() {
    }

    public static OffsetDateTime ofEpochMs(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atOffset(VN);
    }

    public static OffsetDateTime vn(OffsetDateTime time) {
        return time.withOffsetSameInstant(VN);
    }
}
