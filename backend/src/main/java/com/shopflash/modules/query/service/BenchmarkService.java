package com.shopflash.modules.query.service;

import com.shopflash.modules.query.dto.request.BenchmarkRequest;
import com.shopflash.modules.query.dto.request.BenchmarkRequest.BenchmarkVariant;
import com.shopflash.modules.query.dto.response.BenchmarkResponse;
import com.shopflash.modules.query.dto.response.BenchmarkResult;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.service.ReportService.Execution;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Chạy từng biến thể (nguồn + hint/mode) warmup lần bỏ đi rồi runs lần đo. Các biến thể chạy tuần tự.
 *
 * <p>Biến thể mặc định: Oracle AUTO/NL/HASH/MERGE (chỉ AUTO nếu báo cáo không dùng join hint)
 * và Redis AGGREGATE/CLIENT. Biến thể mà nguồn trả supported=false (vd. unsold-products trên Redis)
 * bị bỏ khỏi results, không báo lỗi.
 */
@Service
public class BenchmarkService {

    public static final int DEFAULT_WARMUP = 1;
    public static final int DEFAULT_RUNS = 5;
    public static final int MAX_RUNS = 30;
    /** Khớp @Size(max = 12) của BenchmarkRequest.variants. */
    public static final int MAX_VARIANTS = 12;
    public static final String CSV_HEADER = "report,source,variant,run,elapsedMs,roundTrips";

    private final ReportService reportService;
    private final ReportQueryFactory queryFactory;

    public BenchmarkService(ReportService reportService, ReportQueryFactory queryFactory) {
        this.reportService = reportService;
        this.queryFactory = queryFactory;
    }

    private record Variant(Source source, JoinHint joinHint, RedisMode redisMode) {

        String name() {
            return source == Source.ORACLE ? joinHint.name() : redisMode.name();
        }
    }

    public BenchmarkResponse run(Report report, BenchmarkRequest request) {
        BenchmarkRequest req = request != null ? request : new BenchmarkRequest(null, null, null, null, null, null);
        int warmup = req.warmup() != null ? req.warmup() : DEFAULT_WARMUP;
        int runs = req.runs() != null ? req.runs() : DEFAULT_RUNS;
        if (runs < 1 || runs > MAX_RUNS) {
            throw ApiException.validation("runs", "runs phải từ 1 đến " + MAX_RUNS);
        }
        if (warmup < 0 || warmup > 5) {
            throw ApiException.validation("warmup", "warmup phải từ 0 đến 5");
        }
        int limit = req.limit() != null ? req.limit() : ReportQueryFactory.DEFAULT_LIMIT;
        if (limit < 1 || limit > 100) {
            throw ApiException.validation("limit", "limit phải từ 1 đến 100");
        }
        ReportQuery base = queryFactory.build(report, req.from(), req.to(), JoinHint.AUTO, RedisMode.AGGREGATE,
                limit, ReportQueryFactory.DEFAULT_PAGE, ReportQueryFactory.DEFAULT_SIZE);
        List<Variant> variants = req.variants() == null || req.variants().isEmpty()
                ? defaultVariants(report)
                : parseVariants(req.variants());

        List<BenchmarkResult> results = new ArrayList<>();
        for (Variant v : variants) {
            BenchmarkResult result = runVariant(base.withJoinHint(v.joinHint()).withRedisMode(v.redisMode()),
                    v, warmup, runs);
            if (result != null) {
                results.add(result);
            }
        }
        return new BenchmarkResponse(report.slug(), results);
    }

    /** null nếu nguồn không hỗ trợ báo cáo này. */
    private BenchmarkResult runVariant(ReportQuery query, Variant v, int warmup, int runs) {
        for (int i = 0; i < warmup; i++) {
            if (!reportService.execute(query, v.source()).result().supported()) {
                return null;
            }
        }
        List<Double> samples = new ArrayList<>(runs);
        int roundTrips = 0;
        for (int i = 0; i < runs; i++) {
            Execution e = reportService.execute(query, v.source());
            if (!e.result().supported()) {
                return null;
            }
            samples.add(e.elapsedMs());
            roundTrips = e.result().roundTrips();
        }
        BenchmarkStats stats = BenchmarkStats.of(samples);
        return new BenchmarkResult(ReportService.name(v.source()), v.name(), runs, stats.mean(), stats.stdDev(),
                stats.p50(), stats.p95(), roundTrips, List.copyOf(samples));
    }

    private static List<Variant> defaultVariants(Report report) {
        List<Variant> list = new ArrayList<>();
        if (report.joinHintApplies()) {
            for (JoinHint h : JoinHint.values()) {
                list.add(new Variant(Source.ORACLE, h, RedisMode.AGGREGATE));
            }
        } else {
            list.add(new Variant(Source.ORACLE, JoinHint.AUTO, RedisMode.AGGREGATE));
        }
        for (RedisMode m : RedisMode.values()) {
            list.add(new Variant(Source.REDIS, JoinHint.AUTO, m));
        }
        return list;
    }

    /** Cặp (source, variant) trùng nhau chỉ giữ lần xuất hiện đầu, giữ thứ tự gửi lên. */
    private static List<Variant> parseVariants(List<BenchmarkVariant> requested) {
        if (requested.size() > MAX_VARIANTS) {
            throw ApiException.validation("variants",
                    "variants phải là danh sách tối đa " + MAX_VARIANTS + " phần tử { source, variant }");
        }
        Set<Variant> list = new LinkedHashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            BenchmarkVariant bv = requested.get(i);
            String prefix = "variants[" + i + "]";
            if (bv == null) {
                throw ApiException.validation(prefix, prefix + " không được để trống");
            }
            Source source;
            try {
                source = bv.source() == null || bv.source().isBlank() ? null : Source.from(bv.source());
            } catch (IllegalArgumentException e) {
                source = null;
            }
            if (source == null) {
                throw ApiException.validation(prefix + ".source", prefix + ".source phải là oracle hoặc redis");
            }
            String name = bv.variant() == null ? "" : bv.variant().trim().toUpperCase(Locale.ROOT);
            try {
                if (source == Source.ORACLE) {
                    list.add(new Variant(source, name.isEmpty() ? JoinHint.AUTO : JoinHint.valueOf(name),
                            RedisMode.AGGREGATE));
                } else {
                    list.add(new Variant(source, JoinHint.AUTO,
                            name.isEmpty() ? RedisMode.AGGREGATE : RedisMode.valueOf(name)));
                }
            } catch (IllegalArgumentException e) {
                throw ApiException.validation(prefix + ".variant", source == Source.ORACLE
                        ? prefix + ".variant phải là AUTO, NL, HASH hoặc MERGE"
                        : prefix + ".variant phải là AGGREGATE hoặc CLIENT");
            }
        }
        return List.copyOf(list);
    }

    /** CSV, mỗi mẫu một dòng; roundTrips lấy theo kết quả của biến thể (lần chạy cuối). */
    public static String toCsv(BenchmarkResponse response) {
        StringBuilder sb = new StringBuilder(CSV_HEADER).append('\n');
        for (BenchmarkResult r : response.results()) {
            List<Double> samples = r.samplesMs();
            for (int i = 0; i < samples.size(); i++) {
                sb.append(response.report()).append(',')
                        .append(r.source()).append(',')
                        .append(r.variant()).append(',')
                        .append(i + 1).append(',')
                        .append(BigDecimal.valueOf(samples.get(i)).toPlainString()).append(',')
                        .append(r.roundTrips()).append('\n');
            }
        }
        return sb.toString();
    }
}
