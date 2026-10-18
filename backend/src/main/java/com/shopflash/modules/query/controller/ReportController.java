package com.shopflash.modules.query.controller;

import com.shopflash.modules.query.dto.request.BenchmarkRequest;
import com.shopflash.modules.query.dto.request.StatsRequest;
import com.shopflash.modules.query.dto.response.AnalyticsStatusResponse;
import com.shopflash.modules.query.dto.response.BenchmarkResponse;
import com.shopflash.modules.query.dto.response.CompareResponse;
import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.dto.response.RebuildResponse;
import com.shopflash.modules.query.dto.response.ReportResponse;
import com.shopflash.modules.query.dto.response.StatsResponse;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.service.AnalyticsProjection;
import com.shopflash.modules.query.service.BenchmarkService;
import com.shopflash.modules.query.service.CompareService;
import com.shopflash.modules.query.service.OracleStatsOperations;
import com.shopflash.modules.query.service.ReportQueryFactory;
import com.shopflash.modules.query.service.ReportService;
import jakarta.validation.Valid;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API báo cáo và phòng thí nghiệm Oracle–Redis. Contract: AGENTS.md của module.
 * Tham số query nhận dạng chuỗi rồi ReportQueryFactory kiểm tra, để mọi lỗi có cùng định dạng và thông báo tiếng Việt.
 * Đường dẫn cố định /analytics/** và /oracle/stats có số đoạn khác /{report}, /{report}/... nên không bị nuốt.
 * Ba POST đổi trạng thái (rebuild, oracle/stats, benchmark) chỉ nhận Content-Type: application/json: form HTML không
 * gửi được kiểu này, còn fetch khác origin phải qua preflight CORS (không cấu hình CORS nên bị chặn). Sai kiểu thì 415
 * UNSUPPORTED_MEDIA_TYPE.
 */
// TODO(F0): chỉ cho ADMIN khi common/security có. Trước đó backend chỉ nghe trên 127.0.0.1 (server.address).
@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    public static final MediaType TEXT_CSV_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final ReportQueryFactory queryFactory;
    private final ReportService reportService;
    private final CompareService compareService;
    private final BenchmarkService benchmarkService;
    private final ObjectProvider<AnalyticsProjection> analyticsProjection;
    private final ObjectProvider<OracleStatsOperations> oracleStats;

    public ReportController(ReportQueryFactory queryFactory, ReportService reportService,
            CompareService compareService, BenchmarkService benchmarkService,
            ObjectProvider<AnalyticsProjection> analyticsProjection,
            ObjectProvider<OracleStatsOperations> oracleStats) {
        this.queryFactory = queryFactory;
        this.reportService = reportService;
        this.compareService = compareService;
        this.benchmarkService = benchmarkService;
        this.analyticsProjection = analyticsProjection;
        this.oracleStats = oracleStats;
    }

    @GetMapping("/analytics/status")
    public AnalyticsStatusResponse analyticsStatus() {
        return analytics().status();
    }

    /** Body không có trường nào; gửi {} (hoặc rỗng) kèm Content-Type: application/json. */
    @PostMapping(path = "/analytics/rebuild", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RebuildResponse analyticsRebuild() {
        return analytics().rebuild();
    }

    @PostMapping(path = "/oracle/stats", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StatsResponse oracleStats(@Valid @RequestBody StatsRequest request) {
        OracleStatsOperations ops = require(oracleStats::getIfAvailable,
                new ApiException(ErrorCode.ORACLE_ERROR, "Chức năng thống kê Oracle chưa sẵn sàng"));
        return ops.apply(request.action());
    }

    @GetMapping("/{report}")
    public ReportResponse report(@PathVariable("report") String report,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "joinHint", required = false) String joinHint,
            @RequestParam(name = "redisMode", required = false) String redisMode,
            @RequestParam(name = "limit", required = false) String limit,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        Report r = queryFactory.report(report);
        var src = queryFactory.source(source);
        ReportQuery query = queryFactory.fromParams(r, from, to, joinHint, redisMode, limit, page, size);
        return reportService.run(query, src);
    }

    @GetMapping("/{report}/compare")
    public CompareResponse compare(@PathVariable("report") String report,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "joinHint", required = false) String joinHint,
            @RequestParam(name = "redisMode", required = false) String redisMode,
            @RequestParam(name = "limit", required = false) String limit,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        Report r = queryFactory.report(report);
        ReportQuery query = queryFactory.fromParams(r, from, to, joinHint, redisMode, limit, page, size);
        return compareService.compare(query);
    }

    @GetMapping("/{report}/plan")
    public PlanResponse plan(@PathVariable("report") String report,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "joinHint", required = false) String joinHint,
            @RequestParam(name = "redisMode", required = false) String redisMode,
            @RequestParam(name = "limit", required = false) String limit,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "size", required = false) String size) {
        Report r = queryFactory.report(report);
        var src = queryFactory.source(source);
        ReportQuery query = queryFactory.fromParams(r, from, to, joinHint, redisMode, limit, page, size);
        return reportService.plan(query, src);
    }

    /**
     * JSON mặc định; Accept có text/csv thì trả CSV (mỗi mẫu một dòng). Body được bỏ trống nhưng Content-Type vẫn
     * phải là JSON: với body không bắt buộc, Spring bỏ qua consumes khi request không có body, nên kiểm tra lại ở đây.
     */
    @PostMapping(path = "/{report}/benchmark", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> benchmark(@PathVariable("report") String report,
            @RequestHeader(name = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @Valid @RequestBody(required = false) BenchmarkRequest request) {
        requireJson(contentType);
        Report r = queryFactory.report(report);
        BenchmarkResponse response = benchmarkService.run(r, request);
        if (!wantsCsv(accept)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        }
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"benchmark-" + r.slug() + ".csv\"")
                .body(BenchmarkService.toCsv(response));
    }

    static boolean wantsCsv(String accept) {
        if (accept == null || accept.isBlank()) {
            return false;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(m -> "text".equalsIgnoreCase(m.getType()) && "csv".equalsIgnoreCase(m.getSubtype()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Cùng đường dẫn với ba POST ở trên nhưng không có consumes: Spring chỉ chọn hàm này khi Content-Type không phải
     * JSON. Lỗi 415 do chính Spring ném lúc chọn handler thì không có handler, nên QueryExceptionHandler (giới hạn theo
     * package) không bắt được; ném ở đây để vẫn trả đúng định dạng lỗi của module.
     * Đường dẫn viết dạng biến ({action:rebuild}) để kém cụ thể hơn đường dẫn thật: khi cả hai cùng khớp (request JSON
     * không body tới benchmark), Spring so pattern trước consumes nên luôn chọn hàm thật.
     */
    @PostMapping(path = {"/analytics/{action:rebuild}", "/oracle/{action:stats}", "/{report}/{action:benchmark}"})
    public void rejectNonJson() {
        throw ApiException.unsupportedMediaType();
    }

    /** Content-Type phải tương thích application/json (cho phép kèm charset). */
    static void requireJson(String contentType) {
        boolean json;
        try {
            json = contentType != null && !contentType.isBlank()
                    && MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException e) {
            json = false;
        }
        if (!json) {
            throw ApiException.unsupportedMediaType();
        }
    }

    private AnalyticsProjection analytics() {
        return require(analyticsProjection::getIfAvailable,
                new ApiException(ErrorCode.REDIS_UNAVAILABLE, "Read model Redis chưa sẵn sàng"));
    }

    private static <T> T require(Supplier<T> supplier, ApiException ifMissing) {
        T value = supplier.get();
        if (value == null) {
            throw ifMissing;
        }
        return value;
    }
}
