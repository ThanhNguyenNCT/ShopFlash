package com.shopflash.modules.query.repository;

import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;

/**
 * Một nguồn dữ liệu cho báo cáo: OracleReportSource và RedisReportSource cài interface này,
 * ReportService chọn theo Source. Dạng rows của mỗi báo cáo theo contract trong AGENTS.md của module.
 */
public interface ReportSource {

    Source source();

    /** Chạy báo cáo. Báo cáo không hỗ trợ thì trả SourceResult.unsupported(...), không ném lỗi. */
    SourceResult run(ReportQuery query);

    /** Kế hoạch thực thi: Oracle EXPLAIN PLAN + DBMS_XPLAN, Redis FT.PROFILE hoặc mô tả các lệnh CLIENT. */
    PlanResponse plan(ReportQuery query);
}
