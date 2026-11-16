package com.shopflash.modules.query.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopflash.modules.query.controller.ReportController;
import com.shopflash.modules.query.dto.request.StatsRequest;
import com.shopflash.modules.query.dto.response.AnalyticsStatusResponse;
import com.shopflash.modules.query.dto.response.MonthRevenueRow;
import com.shopflash.modules.query.dto.response.RebuildResponse;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.StatsResponse;
import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;
import com.shopflash.modules.query.exception.QueryExceptionHandler;
import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.service.AnalyticsProjection;
import com.shopflash.modules.query.service.BenchmarkService;
import com.shopflash.modules.query.service.CompareService;
import com.shopflash.modules.query.service.OracleStatsOperations;
import com.shopflash.modules.query.service.ReportQueryFactory;
import com.shopflash.modules.query.service.ReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ReportControllerCoreTest {

    FakeReportSource oracle;
    FakeReportSource redis;
    StaticListableBeanFactory beans;
    List<StatsRequest.Action> statsCalls = new ArrayList<>();
    int rebuildCalls;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        oracle = new FakeReportSource(Source.ORACLE, q -> SourceResult.of(ReportData.of(List.of(
                new MonthRevenueRow("2026-10", 812, new BigDecimal("912345000.50")))),
                q.joinHint().name(), 1, "SELECT ..."));
        redis = new FakeReportSource(Source.REDIS, q -> SourceResult.of(ReportData.of(List.of(
                new MonthRevenueRow("2026-10", 812, new BigDecimal("912345000.5")))),
                q.redisMode().name(), 2, "FT.AGGREGATE ..."));
        beans = new StaticListableBeanFactory();
        build(new ReportService(List.of(oracle, redis), ReportServiceCoreTest.CLOCK));
    }

    void build(ReportService reportService) {
        ReportQueryFactory factory = new ReportQueryFactory(ReportServiceCoreTest.CLOCK);
        ReportController controller = new ReportController(factory, reportService,
                new CompareService(reportService), new BenchmarkService(reportService, factory),
                beans.getBeanProvider(AnalyticsProjection.class), beans.getBeanProvider(OracleStatsOperations.class));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new QueryExceptionHandler())
                .setValidator(validator)
                .build();
    }

    void registerFakes() {
        beans.addBean("analytics", new AnalyticsProjection() {
            @Override
            public AnalyticsStatusResponse status() {
                return new AnalyticsStatusResponse(new AnalyticsStatusResponse.OracleSide(98231, 245102),
                        new AnalyticsStatusResponse.RedisSide(98231, 245102, false,
                                OffsetDateTime.parse("2026-10-18T14:00:00+07:00"), 41230.0), true);
            }

            @Override
            public RebuildResponse rebuild() {
                rebuildCalls++;
                return new RebuildResponse(98231, 245102, 41230.5);
            }
        });
        beans.addBean("stats", (OracleStatsOperations) action -> {
            statsCalls.add(action);
            return new StatsResponse(action.name(), List.of(new StatsResponse.TableStats("ORDERS", 100000L,
                    OffsetDateTime.parse("2026-10-18T14:05:00+07:00"))), 3120.2);
        });
    }

    @Test
    void reportWithDefaults() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue-by-month"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.rows[0].month").value("2026-10"))
                .andExpect(jsonPath("$.data.rows[0].orderCount").value(812))
                .andExpect(jsonPath("$.data.rows[0].revenue").value(912345000.50))
                .andExpect(jsonPath("$.data.page").isEmpty())
                .andExpect(jsonPath("$.meta.source").value("oracle"))
                .andExpect(jsonPath("$.meta.report").value("revenue-by-month"))
                .andExpect(jsonPath("$.meta.variant").value("AUTO"))
                .andExpect(jsonPath("$.meta.supported").value(true))
                .andExpect(jsonPath("$.meta.elapsedMs").isNumber())
                .andExpect(jsonPath("$.meta.roundTrips").value(1))
                .andExpect(jsonPath("$.meta.statement").value("SELECT ..."))
                .andExpect(jsonPath("$.meta.rowCount").value(1))
                .andExpect(jsonPath("$.meta.generatedAt").value("2026-10-18T10:00:00+07:00"))
                .andExpect(jsonPath("$.meta.note").isEmpty());

        assertThat(redis.runs).isEmpty();
        ReportQuery q = oracle.runs.get(0);
        assertThat(q).isEqualTo(new ReportQuery(Report.REVENUE_BY_MONTH, LocalDate.of(2025, 11, 1),
                LocalDate.of(2026, 10, 18), JoinHint.AUTO, RedisMode.AGGREGATE, 10, 1, 20));
    }

    @Test
    void moneyIsJsonNumber() throws Exception {
        String body = mvc.perform(get("/api/admin/reports/revenue-by-month"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"revenue\":912345000.50").contains("\"generatedAt\":\"2026-10-18T10:00:00+07:00\"");
    }

    @Test
    void routesToRedisWithParamsCaseInsensitive() throws Exception {
        mvc.perform(get("/api/admin/reports/vip-customers")
                        .param("source", "REDIS").param("redisMode", "client").param("joinHint", "hash")
                        .param("from", "2026-01-01").param("to", "2026-01-31")
                        .param("limit", "5").param("page", "3").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.source").value("redis"))
                .andExpect(jsonPath("$.meta.variant").value("CLIENT"))
                .andExpect(jsonPath("$.meta.roundTrips").value(2));
        assertThat(oracle.runs).isEmpty();
        assertThat(redis.runs.get(0)).isEqualTo(new ReportQuery(Report.VIP_CUSTOMERS, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31), JoinHint.HASH, RedisMode.CLIENT, 5, 3, 100));
    }

    static Stream<Arguments> invalidParams() {
        return Stream.of(
                Arguments.of("source", "mongo", "source", "source phải là oracle, redis hoặc cache"),
                Arguments.of("from", "2026-13-01", "from", "from phải là ngày hợp lệ dạng YYYY-MM-DD"),
                Arguments.of("to", "18/10/2026", "to", "to phải là ngày hợp lệ dạng YYYY-MM-DD"),
                Arguments.of("from", "2026-10-19", "from", "from không được sau to"),
                Arguments.of("joinHint", "LOOP", "joinHint", "joinHint phải là AUTO, NL, HASH hoặc MERGE"),
                Arguments.of("redisMode", "PIPELINE", "redisMode", "redisMode phải là AGGREGATE hoặc CLIENT"),
                Arguments.of("limit", "0", "limit", "limit phải từ 1 đến 100"),
                Arguments.of("limit", "101", "limit", "limit phải từ 1 đến 100"),
                Arguments.of("limit", "abc", "limit", "limit phải từ 1 đến 100"),
                Arguments.of("page", "0", "page", "page phải từ 1 đến 100000"),
                Arguments.of("page", "1.5", "page", "page phải từ 1 đến 100000"),
                Arguments.of("page", "100001", "page", "page phải từ 1 đến 100000"),
                Arguments.of("page", "2147483647", "page", "page phải từ 1 đến 100000"),
                Arguments.of("page", "99999999999", "page", "page phải từ 1 đến 100000"),
                Arguments.of("from", "1999-12-31", "from", "from phải trong khoảng năm 2000 đến 2100"),
                Arguments.of("to", "2101-01-01", "to", "to phải trong khoảng năm 2000 đến 2100"),
                Arguments.of("from", "+99999-01-01", "from", "from phải trong khoảng năm 2000 đến 2100"),
                Arguments.of("size", "0", "size", "size phải từ 1 đến 100"),
                Arguments.of("size", "101", "size", "size phải từ 1 đến 100"));
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("invalidParams")
    void invalidParamIs400WithField(String param, String value, String field, String message) throws Exception {
        mvc.perform(get("/api/admin/reports/top-products").param(param, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(message))
                .andExpect(jsonPath("$.error.details.field").value(field));
        assertThat(oracle.runs).isEmpty();
    }

    @Test
    void fromAfterToAlsoRejectedOnCompareAndPlan() throws Exception {
        mvc.perform(get("/api/admin/reports/top-products/compare").param("from", "2026-05-02").param("to", "2026-05-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("from"));
        mvc.perform(get("/api/admin/reports/top-products/plan").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("size"));
    }

    @Test
    void unknownReportIs400() throws Exception {
        for (String path : List.of("/api/admin/reports/nope", "/api/admin/reports/nope/compare",
                "/api/admin/reports/nope/plan")) {
            mvc.perform(get(path))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.message").value("Báo cáo 'nope' không tồn tại"))
                    .andExpect(jsonPath("$.error.details.field").value("report"))
                    .andExpect(jsonPath("$.error.details.allowed[0]").value("revenue-by-month"));
        }
        mvc.perform(post("/api/admin/reports/analytics/benchmark").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("report"));
    }

    @Test
    void compareReturnsBothSides() throws Exception {
        mvc.perform(get("/api/admin/reports/revenue-by-month/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report").value("revenue-by-month"))
                .andExpect(jsonPath("$.oracle.meta.source").value("oracle"))
                .andExpect(jsonPath("$.redis.meta.source").value("redis"))
                .andExpect(jsonPath("$.match").value(true))
                .andExpect(jsonPath("$.mismatches").isEmpty());
    }

    @Test
    void compareMismatchShape() throws Exception {
        redis.behavior = q -> SourceResult.of(ReportData.of(List.of(
                new MonthRevenueRow("2026-10", 812, new BigDecimal("1")))), "AGGREGATE", 1, "FT");
        mvc.perform(get("/api/admin/reports/revenue-by-month/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").value(false))
                .andExpect(jsonPath("$.mismatches[0].key").value("2026-10"))
                .andExpect(jsonPath("$.mismatches[0].field").value("revenue"))
                .andExpect(jsonPath("$.mismatches[0].oracle").value(912345000.50))
                .andExpect(jsonPath("$.mismatches[0].redis").value(1.00));
    }

    @Test
    void compareWithUnsupportedSideHasNullMatch() throws Exception {
        redis.behavior = q -> SourceResult.unsupported("AGGREGATE", "Redis không hỗ trợ");
        mvc.perform(get("/api/admin/reports/unsold-products/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.redis.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.redis.meta.supported").value(false))
                .andExpect(jsonPath("$.redis.meta.note").value("Redis không hỗ trợ"));
    }

    @Test
    void planUsesSourceAndHint() throws Exception {
        mvc.perform(get("/api/admin/reports/top-products/plan").param("joinHint", "MERGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("oracle"))
                .andExpect(jsonPath("$.variant").value("MERGE"))
                .andExpect(jsonPath("$.lines[1]").value("line 2"));
        mvc.perform(get("/api/admin/reports/top-products/plan").param("source", "redis"))
                .andExpect(jsonPath("$.source").value("redis"))
                .andExpect(jsonPath("$.variant").value("AGGREGATE"));
        assertThat(oracle.runs).isEmpty();
    }

    @Test
    void benchmarkJsonByDefault() throws Exception {
        mvc.perform(post("/api/admin/reports/revenue-by-month/benchmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runs\":2,\"warmup\":0}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.report").value("revenue-by-month"))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].source").value("oracle"))
                .andExpect(jsonPath("$.results[0].variant").value("AUTO"))
                .andExpect(jsonPath("$.results[0].runs").value(2))
                .andExpect(jsonPath("$.results[0].samplesMs.length()").value(2))
                .andExpect(jsonPath("$.results[2].variant").value("CLIENT"));
        assertThat(oracle.runs).hasSize(2);
    }

    @Test
    void benchmarkWithEmptyJsonBodyUsesDefaults() throws Exception {
        mvc.perform(post("/api/admin/reports/top-products/benchmark").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(6))
                .andExpect(jsonPath("$.results[0].runs").value(5));
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType("application/json;charset=UTF-8").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(6));
    }

    @Test
    void maxPageHasNonNegativeOffset() throws Exception {
        mvc.perform(get("/api/admin/reports/vip-customers").param("page", "100000").param("size", "100"))
                .andExpect(status().isOk());
        assertThat(oracle.runs.get(0).offset()).isEqualTo(9_999_900);
        ReportQuery huge = new ReportQuery(Report.VIP_CUSTOMERS, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2),
                JoinHint.AUTO, RedisMode.AGGREGATE, 10, Integer.MAX_VALUE, 100);
        org.assertj.core.api.Assertions.assertThatThrownBy(huge::offset).isInstanceOf(ArithmeticException.class);
    }

    static Stream<Arguments> jsonOnlyPosts() {
        return Stream.of(
                Arguments.of("/api/admin/reports/analytics/rebuild"),
                Arguments.of("/api/admin/reports/oracle/stats"),
                Arguments.of("/api/admin/reports/top-products/benchmark"));
    }

    /** CSRF: form HTML (urlencoded, multipart, text/plain) hoặc không có Content-Type đều bị 415, không chạy gì. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonOnlyPosts")
    void stateChangingPostsRequireJson(String path) throws Exception {
        registerFakes();
        List<org.springframework.test.web.servlet.RequestBuilder> requests = List.of(
                post(path),
                post(path).contentType(MediaType.APPLICATION_FORM_URLENCODED),
                post(path).contentType(MediaType.APPLICATION_FORM_URLENCODED).content("action=GATHER"),
                post(path).contentType(MediaType.TEXT_PLAIN).content("{\"action\":\"GATHER\"}"),
                post(path).contentType(MediaType.MULTIPART_FORM_DATA).content("x"));
        for (var request : requests) {
            mvc.perform(request)
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
                    .andExpect(jsonPath("$.error.message").value("Content-Type phải là application/json"))
                    .andExpect(jsonPath("$.error.details.supported[0]").value("application/json"));
        }
        assertThat(rebuildCalls).isZero();
        assertThat(statsCalls).isEmpty();
        assertThat(oracle.runs).isEmpty();
        assertThat(redis.runs).isEmpty();
    }

    @Test
    void rebuildAcceptsEmptyJson() throws Exception {
        registerFakes();
        mvc.perform(post("/api/admin/reports/analytics/rebuild").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(98231));
        mvc.perform(post("/api/admin/reports/analytics/rebuild").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        assertThat(rebuildCalls).isEqualTo(2);
    }

    @Test
    void rebuildInProgressIs409() throws Exception {
        beans.addBean("analytics", new AnalyticsProjection() {
            @Override
            public AnalyticsStatusResponse status() {
                throw new UnsupportedOperationException();
            }

            @Override
            public RebuildResponse rebuild() {
                throw new ApiException(ErrorCode.REBUILD_IN_PROGRESS, "Đang có một lần dựng lại read model Redis");
            }
        });
        mvc.perform(post("/api/admin/reports/analytics/rebuild").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REBUILD_IN_PROGRESS"))
                .andExpect(jsonPath("$.error.message").value("Đang có một lần dựng lại read model Redis"));
    }

    @Test
    void benchmarkVariantsMax12AndDeduplicated() throws Exception {
        String variant = "{\"source\":\"oracle\",\"variant\":\"AUTO\"}";
        String thirteen = "{\"runs\":1,\"warmup\":0,\"variants\":["
                + String.join(",", java.util.Collections.nCopies(13, variant)) + "]}";
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content(thirteen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message")
                        .value("variants phải là danh sách tối đa 12 phần tử { source, variant }"))
                .andExpect(jsonPath("$.error.details.field").value("variants"));
        assertThat(oracle.runs).isEmpty();

        // 12 phần tử trùng nhau: hợp lệ, chỉ chạy một biến thể.
        String twelve = "{\"runs\":1,\"warmup\":0,\"variants\":["
                + String.join(",", java.util.Collections.nCopies(12, variant)) + "]}";
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content(twelve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1));
        assertThat(oracle.runs).hasSize(1);
    }

    @Test
    void benchmarkBodyYearRange() throws Exception {
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"1970-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("from phải trong khoảng năm 2000 đến 2100"))
                .andExpect(jsonPath("$.error.details.field").value("from"));
    }

    @Test
    void benchmarkCsvWhenAccepted() throws Exception {
        String csv = mvc.perform(post("/api/admin/reports/revenue-by-month/benchmark")
                        .accept("text/csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runs\":2,\"warmup\":0,\"variants\":[{\"source\":\"oracle\",\"variant\":\"AUTO\"}]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("benchmark-revenue-by-month.csv")))
                .andReturn().getResponse().getContentAsString();
        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo("report,source,variant,run,elapsedMs,roundTrips");
        assertThat(lines[1]).matches("revenue-by-month,oracle,AUTO,1,\\d+(\\.\\d+)?,1");
        assertThat(lines[2]).matches("revenue-by-month,oracle,AUTO,2,\\d+(\\.\\d+)?,1");
    }

    @Test
    void benchmarkErrorsStayJsonEvenWhenCsvAccepted() throws Exception {
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .accept("text/csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runs\":31}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("runs phải từ 1 đến 30"))
                .andExpect(jsonPath("$.error.details.field").value("runs"));
    }

    @Test
    void benchmarkBodyValidation() throws Exception {
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("limit phải từ 1 đến 100"))
                .andExpect(jsonPath("$.error.details.field").value("limit"));
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"warmup\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("warmup"));
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"2026-02-30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.field").value("from"));
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"runs\":\"many\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("runs"));
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.field").value("body"));
        mvc.perform(post("/api/admin/reports/top-products/benchmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variants\":[{\"source\":\"redis\",\"variant\":\"HASH\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("variants[0].variant phải là AGGREGATE hoặc CLIENT"))
                .andExpect(jsonPath("$.error.details.field").value("variants[0].variant"));
    }

    @Test
    void literalRoutesAreNotCapturedByReportSlug() throws Exception {
        registerFakes();
        mvc.perform(get("/api/admin/reports/analytics/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oracle.countedOrders").value(98231))
                .andExpect(jsonPath("$.redis.lastRebuildAt").value("2026-10-18T14:00:00+07:00"))
                .andExpect(jsonPath("$.inSync").value(true));
        mvc.perform(post("/api/admin/reports/analytics/rebuild").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").value(98231))
                .andExpect(jsonPath("$.elapsedMs").value(41230.5));
        mvc.perform(post("/api/admin/reports/oracle/stats")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"GATHER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("GATHER"))
                .andExpect(jsonPath("$.tables[0].table").value("ORDERS"))
                .andExpect(jsonPath("$.tables[0].lastAnalyzed").value("2026-10-18T14:05:00+07:00"));
        assertThat(statsCalls).containsExactly(StatsRequest.Action.GATHER);
        assertThat(oracle.runs).isEmpty();
        assertThat(redis.runs).isEmpty();
    }

    @Test
    void statsRequestValidation() throws Exception {
        registerFakes();
        mvc.perform(post("/api/admin/reports/oracle/stats").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("action phải là GATHER hoặc DELETE"))
                .andExpect(jsonPath("$.error.details.field").value("action"));
        mvc.perform(post("/api/admin/reports/oracle/stats").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"TRUNCATE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("action"));
        mvc.perform(post("/api/admin/reports/oracle/stats").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        assertThat(statsCalls).isEmpty();
    }

    @Test
    void missingImplementationsGive503And500() throws Exception {
        mvc.perform(get("/api/admin/reports/analytics/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("REDIS_UNAVAILABLE"));
        mvc.perform(post("/api/admin/reports/oracle/stats")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"DELETE\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("ORACLE_ERROR"));
        build(new ReportService(List.of(oracle), ReportServiceCoreTest.CLOCK));
        mvc.perform(get("/api/admin/reports/top-products").param("source", "redis"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("REDIS_UNAVAILABLE"));
    }

    @Test
    void unexpectedExceptionIs500WithoutStackTrace() throws Exception {
        oracle.behavior = q -> {
            throw new IllegalStateException("secret internal detail");
        };
        mvc.perform(get("/api/admin/reports/top-products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Lỗi hệ thống, vui lòng thử lại"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void dataAccessExceptionIsOracleError() throws Exception {
        oracle.behavior = q -> {
            throw new org.springframework.dao.DataAccessResourceFailureException(
                    "ORA-12541: Cannot connect. No listener at host 10.0.0.5 port 1521");
        };
        mvc.perform(get("/api/admin/reports/top-products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("ORACLE_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Lỗi khi truy vấn Oracle"))
                .andExpect(jsonPath("$.error.details.oraCode").value("ORA-12541"))
                .andExpect(content().string(not(containsString("listener"))))
                .andExpect(content().string(not(containsString("10.0.0.5"))));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/reports/top-products"))
                .andExpect(status().isMethodNotAllowed());
    }
}
