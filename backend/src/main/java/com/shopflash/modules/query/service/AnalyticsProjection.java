package com.shopflash.modules.query.service;

import com.shopflash.modules.query.dto.response.AnalyticsStatusResponse;
import com.shopflash.modules.query.dto.response.RebuildResponse;

/** Read model Redis (order:analytics:*, order:line:*) dựng từ Oracle. Cài đặt: AnalyticsProjectionService. */
public interface AnalyticsProjection {

    AnalyticsStatusResponse status();

    /** Xóa và nạp lại toàn bộ document từ Oracle; trả khi xong. */
    RebuildResponse rebuild();
}
