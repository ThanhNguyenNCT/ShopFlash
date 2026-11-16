package com.shopflash.modules.query.service;

import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.dto.response.ReportMeta;
import com.shopflash.modules.query.dto.response.ReportResponse;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.ReportSource;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Chọn ReportSource theo Source, đo thời gian quanh run() và dựng meta.
 * Thiếu bean nguồn nào thì chỉ lỗi khi gọi tới nguồn đó, không làm hỏng lúc khởi động.
 */
@Service
public class ReportService {

    private final Map<Source, ReportSource> sources = new EnumMap<>(Source.class);
    private final Clock clock;

    @Autowired
    public ReportService(ObjectProvider<ReportSource> sources) {
        this(sources.orderedStream().toList(), Clock.system(ReportQuery.ZONE));
    }

    public ReportService(List<ReportSource> sources, Clock clock) {
        for (ReportSource s : sources) {
            this.sources.putIfAbsent(s.source(), s);
        }
        this.clock = clock;
    }

    /** Một lần chạy đã đo: kết quả của nguồn và thời gian (ms, 2 chữ số thập phân). */
    public record Execution(Source source, SourceResult result, double elapsedMs) {
    }

    public ReportResponse run(ReportQuery query, Source source) {
        return toResponse(query, execute(query, source));
    }

    public Execution execute(ReportQuery query, Source source) {
        ReportSource target = sourceFor(source);
        long start = System.nanoTime();
        SourceResult result = target.run(query);
        long elapsedNanos = System.nanoTime() - start;
        if (result == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Nguồn " + name(source) + " không trả kết quả");
        }
        return new Execution(source, result, toMillis(elapsedNanos));
    }

    public ReportResponse toResponse(ReportQuery query, Execution execution) {
        SourceResult r = execution.result();
        int rowCount = r.data() == null || r.data().rows() == null ? 0 : r.data().rows().size();
        ReportMeta meta = new ReportMeta(
                name(execution.source()),
                query.report().slug(),
                r.variant(),
                r.supported(),
                execution.elapsedMs(),
                r.roundTrips(),
                r.statement(),
                rowCount,
                OffsetDateTime.now(clock.withZone(ReportQuery.ZONE)).truncatedTo(ChronoUnit.SECONDS),
                r.note());
        return new ReportResponse(r.supported() ? r.data() : null, meta);
    }

    public PlanResponse plan(ReportQuery query, Source source) {
        return sourceFor(source).plan(query);
    }

    public static String name(Source source) {
        return source.name().toLowerCase(Locale.ROOT);
    }

    /** nano giây sang ms, làm tròn 2 chữ số. */
    public static double toMillis(long nanos) {
        return Math.round(nanos / 10_000.0) / 100.0;
    }

    private ReportSource sourceFor(Source source) {
        ReportSource target = sources.get(source);
        if (target != null) {
            return target;
        }
        if (source == Source.REDIS || source == Source.CACHE) {
            throw new ApiException(ErrorCode.REDIS_UNAVAILABLE, "Nguồn " + name(source) + " chưa sẵn sàng");
        }
        throw new ApiException(ErrorCode.ORACLE_ERROR, "Nguồn Oracle chưa sẵn sàng");
    }
}
