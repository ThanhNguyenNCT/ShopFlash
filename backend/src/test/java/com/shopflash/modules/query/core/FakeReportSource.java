package com.shopflash.modules.query.core;

import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.ReportSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** ReportSource giả: trả kết quả do test quyết định và ghi lại mọi query đã nhận. */
class FakeReportSource implements ReportSource {

    final Source source;
    final List<ReportQuery> runs = new ArrayList<>();
    final List<ReportQuery> plans = new ArrayList<>();
    Function<ReportQuery, SourceResult> behavior;

    FakeReportSource(Source source, Function<ReportQuery, SourceResult> behavior) {
        this.source = source;
        this.behavior = behavior;
    }

    @Override
    public Source source() {
        return source;
    }

    @Override
    public SourceResult run(ReportQuery query) {
        runs.add(query);
        return behavior.apply(query);
    }

    @Override
    public PlanResponse plan(ReportQuery query) {
        plans.add(query);
        String variant = source == Source.ORACLE ? query.joinHint().name() : query.redisMode().name();
        return new PlanResponse(source.name().toLowerCase(Locale.ROOT), variant, "STATEMENT", List.of("line 1", "line 2"));
    }
}
