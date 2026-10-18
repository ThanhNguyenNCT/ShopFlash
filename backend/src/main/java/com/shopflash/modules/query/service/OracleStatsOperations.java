package com.shopflash.modules.query.service;

import com.shopflash.modules.query.dto.request.StatsRequest;
import com.shopflash.modules.query.dto.response.StatsResponse;

/** Gather/Delete thống kê của các bảng báo cáo trong schema SHOP. Cài đặt: OracleStatsService. */
public interface OracleStatsOperations {

    StatsResponse apply(StatsRequest.Action action);
}
