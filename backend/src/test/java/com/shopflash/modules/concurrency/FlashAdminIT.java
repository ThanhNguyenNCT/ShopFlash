package com.shopflash.modules.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.shopflash.common.security.AuthService;
import com.shopflash.common.security.LoginRequest;
import com.shopflash.modules.concurrency.repository.redis.FlashKeys;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;
import com.shopflash.modules.concurrency.service.FlashLifecycleService;
import com.shopflash.modules.concurrency.service.FlashRuntimeService;

import jakarta.servlet.Filter;
import redis.clients.jedis.RedisClient;

/**
 * F9 phía admin qua HTTP trên Oracle và Redis thật: ./mvnw test -Dtest='FlashAdminIT'
 * Dùng sản phẩm riêng 990301-990304 (SKU CCADM-*), xóa sạch chương trình và sản phẩm sau khi chạy.
 * Job định kỳ tắt; test tự gọi activateDue, endDue. Mốc an toàn khi kết thúc đặt 0 s cho nhanh.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "shopflash.flash.jobs.enabled=false",
        "shopflash.flash.end-grace-seconds=0" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlashAdminIT {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("flash-admin-test-secret-32-bytes!!!!".getBytes());
    private static final long FRYER = 990301;      // base 500 000, kho 50
    private static final long KETTLE = 990302;     // base 300 000, kho 50
    private static final long RETIRED = 990303;    // INACTIVE
    private static final long SCARCE = 990304;     // kho 3
    private static final List<Long> PRODUCTS = List.of(FRYER, KETTLE, RETIRED, SCARCE);

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired RedisClient redis;
    @Autowired FlashRuntimeRepository runtime;
    @Autowired FlashRuntimeService runtimeService;
    @Autowired FlashLifecycleService lifecycle;
    @Autowired AuthService auth;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;

    private final List<Long> sales = new ArrayList<>();
    private MockMvc mvc;
    private String admin;
    private String customer;

    @BeforeAll
    void createProducts() {
        removeFixture();
        insertProduct(FRYER, "CCADM Nồi chiên", 500000, "ACTIVE", 50);
        insertProduct(KETTLE, "CCADM Ấm siêu tốc", 300000, "ACTIVE", 50);
        insertProduct(RETIRED, "CCADM Quạt cũ", 200000, "INACTIVE", 50);
        insertProduct(SCARCE, "CCADM Hàng hiếm", 900000, "ACTIVE", 3);
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
        admin = login("admin@shopflash.local");
        customer = login("cc010@shopflash.local");
    }

    @BeforeEach
    void resetStock() {
        removeSales();
        jdbc.update("""
                UPDATE product_inventory SET reserved_flash_quantity = 0,
                       on_hand_quantity = CASE product_id WHEN :scarce THEN 3 ELSE 50 END
                WHERE product_id IN (:ids)
                """, Map.of("ids", PRODUCTS, "scarce", SCARCE));
    }

    @AfterAll
    void cleanUp() {
        removeFixture();
    }

    @Test
    void draftIsEditedThenScheduledThenUnscheduled() throws Exception {
        long id = createDraft(3600, 7200);
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(0,
                        item(FRYER, 99000, 10), item(KETTLE, 149000, 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items.length()").value(2));

        // Form cũ (version 0) gửi lại: người khác đã sửa trước.
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(0, item(FRYER, 99000, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FLASH_SALE_CHANGED"));
        // Quy tắc sản phẩm.
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(1, item(FRYER, 500000, 1))))
                .andExpect(status().isBadRequest());
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(1, item(FRYER, 99000, 51))))
                .andExpect(status().isBadRequest());
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(1, item(RETIRED, 1000, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_UNAVAILABLE"));
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(1, item(999_999_999L, 1000, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id),
                        items(1, item(FRYER, 1000, 1), item(FRYER, 1000, 2))))
                .andExpect(status().isBadRequest());

        mvc.perform(asAdmin(get("/api/admin/flash-sales").param("status", "DRAFT").param("size", "100"), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.flashSaleId == " + id + ")].allocatedTotal").value(15));
        mvc.perform(asAdmin(get("/api/admin/flash-sales/eligible-products").param("saleId", String.valueOf(id))
                        .param("q", "ccadm"), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))   // FRYER, KETTLE, SCARCE; RETIRED ngừng bán
                .andExpect(jsonPath("$.rows[?(@.productId == " + RETIRED + ")]").isEmpty());

        // Lên lịch: giữ hàng trong kho và nạp Redis.
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/schedule", id), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        assertThat(reserved(FRYER)).isEqualTo(10);
        assertThat(reserved(KETTLE)).isEqualTo(5);
        assertThat(runtime.saleLoaded(id)).isTrue();
        assertThat(invariantViolations()).isZero();

        // Chương trình khác trùng giờ không chọn được sản phẩm này nữa (NOT EXISTS).
        long other = createDraft(5400, 9000);
        mvc.perform(asAdmin(get("/api/admin/flash-sales/eligible-products").param("saleId", String.valueOf(other))
                        .param("q", "ccadm"), null))
                .andExpect(jsonPath("$.total").value(1));  // chỉ còn SCARCE

        // Đã lên lịch: chỉ sửa tên, mô tả; không sửa giờ, không thay sản phẩm.
        Map<String, Object> current = detail(id);
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}", id), saveBody(current, "Tên mới",
                        (String) current.get("startAt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tên mới"));
        current = detail(id);
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}", id), saveBody(current, "Tên mới",
                        OffsetDateTime.parse((String) current.get("startAt")).plusMinutes(5).toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id),
                        items(((Number) current.get("version")).longValue(), item(FRYER, 99000, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
        mvc.perform(asAdmin(delete("/api/admin/flash-sales/{id}", id), null)).andExpect(status().isConflict());

        // Hủy lịch: trả hàng về kho, xóa runtime.
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/unschedule", id), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(reserved(FRYER)).isZero();
        assertThat(reserved(KETTLE)).isZero();
        assertThat(runtime.saleLoaded(id)).isFalse();

        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/cancel", id), null))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/schedule", id), null))
                .andExpect(status().isConflict());
        mvc.perform(asAdmin(delete("/api/admin/flash-sales/{id}", other), null)).andExpect(status().isNoContent());
        mvc.perform(asAdmin(get("/api/admin/flash-sales/{id}", other), null)).andExpect(status().isNotFound());
    }

    @Test
    void schedulingIsAllOrNothing() throws Exception {
        long id = createDraft(3600, 7200);
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(0,
                item(FRYER, 99000, 10), item(SCARCE, 500000, 3)))).andExpect(status().isOk());
        // Hàng thường bán bớt sau khi soạn: kho SCARCE còn 2 < 3.
        jdbc.update("UPDATE product_inventory SET on_hand_quantity = 2 WHERE product_id = :id", Map.of("id", SCARCE));

        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/schedule", id), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.error.details.productId").value(SCARCE));

        // Rollback cả phần đã giữ cho FRYER (khóa và giữ theo product_id tăng dần, FRYER đứng trước).
        assertThat(reserved(FRYER)).isZero();
        assertThat((String) detail(id).get("status")).isEqualTo("DRAFT");
        assertThat(runtime.saleLoaded(id)).isFalse();
    }

    @Test
    void twoDraftsWithSameProductAndTimeOnlyOneIsScheduled() throws Exception {
        // Lúc soạn, cả hai đều hợp lệ: kiểm tra trùng giờ chỉ so với chương trình đã lên lịch.
        long first = createDraft(3600, 7200);
        long second = createDraft(5400, 9000);
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", first), items(0, item(FRYER, 99000, 10))))
                .andExpect(status().isOk());
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", second), items(0,
                item(FRYER, 89000, 5), item(KETTLE, 149000, 5)))).andExpect(status().isOk());

        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/schedule", first), null)).andExpect(status().isOk());
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/schedule", second), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.details.productId").value(FRYER));

        // Chương trình thứ hai rollback hết: vẫn DRAFT, không giữ thêm hàng, không nạp Redis.
        assertThat((String) detail(second).get("status")).isEqualTo("DRAFT");
        assertThat(reserved(FRYER)).isEqualTo(10);
        assertThat(reserved(KETTLE)).isZero();
        assertThat(runtime.saleLoaded(second)).isFalse();
        assertThat(invariantViolations()).isZero();
    }

    @Test
    void concurrentAdminsEditingOneDraftOnlyOneWins() throws Exception {
        long id = createDraft(3600, 7200);
        int admins = 6;
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(admins)) {
            for (int i = 0; i < admins; i++) {
                int quota = i + 1;
                results.add(pool.submit(() -> {
                    gate.await();
                    return mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id),
                            items(0, item(FRYER, 99000, quota)))).andReturn().getResponse().getStatus();
                }));
            }
            gate.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get());
            }
            assertThat(codes).containsOnlyOnce(200);
            assertThat(codes).filteredOn(c -> c != 200).containsOnly(409);
        }
        assertThat(((Number) detail(id).get("version")).longValue()).isEqualTo(1);
    }

    @Test
    void jobOpensSaleAdminStopsItJobEndsItAndReturnsUnsoldStock() throws Exception {
        long id = createDraft(5, 3600);
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(0, item(FRYER, 99000, 10))))
                .andExpect(status().isOk());
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/schedule", id), null)).andExpect(status().isOk());
        jdbc.update("UPDATE flash_sale_configs SET reservation_timeout_seconds = 1 WHERE flash_sale_id = :id",
                Map.of("id", id));
        long itemId = itemIdOf(id);

        waitForOracleTime(id);
        assertThat(lifecycle.activateDue()).contains(id);
        assertThat((String) detail(id).get("status")).isEqualTo("ACTIVE");
        assertThat(redis.hget(FlashKeys.sale(id), "status")).isEqualTo("ACTIVE");

        // Khách mua 1 suất.
        MvcResult reserved = mvc.perform(post("/api/flash-sales/{s}/items/{i}/reservations", id, itemId)
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isCreated()).andReturn();
        String reservationId = JsonPath.read(reserved.getResponse().getContentAsString(), "$.reservationId");
        mvc.perform(post("/api/flash-sales/reservations/{r}/checkout", reservationId)
                        .header("Authorization", "Bearer " + customer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"B\",\"recipientPhone\":\"0911111111\",\"shippingAddress\":\"HN\"}"))
                .andExpect(status().isCreated());

        // Dừng sớm: Redis chặn giữ suất ngay.
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/stop", id), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/flash-sales/{s}/items/{i}/reservations", id, itemId)
                        .header("Authorization", "Bearer " + login("cc011@shopflash.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SALE_ENDED"));

        // Sau end_at + 1 s giữ suất (+ 0 s mốc an toàn): job kết thúc, trả 9 suất chưa bán về kho thường.
        Thread.sleep(1500);
        assertThat(lifecycle.endDue()).contains(id);
        assertThat((String) detail(id).get("status")).isEqualTo("ENDED");
        assertThat(reserved(FRYER)).isZero();
        assertThat(onHand(FRYER)).isEqualTo(49);
        assertThat(runtime.saleLoaded(id)).isFalse();
        assertThat(invariantViolations()).isZero();
        redis.unlink(FlashKeys.reservation(reservationId));
    }

    @Test
    void runtimeViewDetectsMissingKeysAndRebuildFixesThem() throws Exception {
        long id = createDraft(3600, 7200);
        mvc.perform(asAdmin(put("/api/admin/flash-sales/{id}/items", id), items(0, item(KETTLE, 149000, 5))))
                .andExpect(status().isOk());
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/schedule", id), null)).andExpect(status().isOk());
        long itemId = itemIdOf(id);

        mvc.perform(asAdmin(get("/api/admin/flash-sales/{id}/runtime", id), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtimeLoaded").value(true))
                .andExpect(jsonPath("$.items[0].remaining").value(5))
                .andExpect(jsonPath("$.items[0].held").value(0))
                .andExpect(jsonPath("$.items[0].inSync").value(true))
                .andExpect(jsonPath("$.invariantViolations").isEmpty());

        redis.unlink(FlashKeys.item(itemId));  // mất key (Redis bị xóa một phần)
        mvc.perform(asAdmin(get("/api/admin/flash-sales/{id}/runtime", id), null))
                .andExpect(jsonPath("$.items[0].remaining").doesNotExist())
                .andExpect(jsonPath("$.items[0].inSync").value(false));

        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/runtime/rebuild", id), null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").value(1));
        mvc.perform(asAdmin(get("/api/admin/flash-sales/{id}/runtime", id), null))
                .andExpect(jsonPath("$.items[0].remaining").value(5))
                .andExpect(jsonPath("$.items[0].inSync").value(true));

        long draft = createDraft(3600, 7200);
        mvc.perform(asAdmin(post("/api/admin/flash-sales/{id}/runtime/rebuild", draft), null))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyAdminsManageSalesAndInputIsValidated() throws Exception {
        mvc.perform(get("/api/admin/flash-sales").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mvc.perform(asAdmin(get("/api/admin/flash-sales").param("status", "NOPE"), null))
                .andExpect(status().isBadRequest());
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(7)).plusHours(1);
        mvc.perform(asAdmin(post("/api/admin/flash-sales"), Map.of("name", "X", "startAt", start.toString(),
                        "endAt", start.minusMinutes(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("endAt"));
        mvc.perform(asAdmin(post("/api/admin/flash-sales"), Map.of("name", " ", "startAt", start.toString(),
                        "endAt", start.plusHours(1).toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(asAdmin(post("/api/admin/flash-sales"), Map.of("name", "X", "startAt", start.toString(),
                        "endAt", start.plusHours(1).toString(), "config", Map.of("reservationTimeoutSeconds", 5))))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private long createDraft(int startInSeconds, int endInSeconds) throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(7));
        MvcResult result = mvc.perform(asAdmin(post("/api/admin/flash-sales"), Map.of(
                        "name", "CCADM test", "description", "Chương trình test",
                        "startAt", now.plusSeconds(startInSeconds).toString(),
                        "endAt", now.plusSeconds(endInSeconds).toString(),
                        "config", Map.of("maxQuantityPerUser", 2, "reservationTimeoutSeconds", 60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        long id = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.flashSaleId")).longValue();
        sales.add(id);
        return id;
    }

    private RequestBuilder asAdmin(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
            Object body) throws Exception {
        builder.header("Authorization", "Bearer " + admin);
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return builder;
    }

    private static Map<String, Object> items(long version, Map<?, ?>... items) {
        return Map.of("version", version, "items", List.of(items));
    }

    private static Map<String, Object> item(long productId, long salePrice, int quantity) {
        return Map.of("productId", productId, "salePrice", salePrice, "allocatedQuantity", quantity);
    }

    private static Map<String, Object> saveBody(Map<String, Object> current, String name, String startAt) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) current.get("config");
        return Map.of("version", current.get("version"), "name", name, "startAt", startAt,
                "endAt", current.get("endAt"), "config", Map.of(
                        "maxQuantityPerUser", config.get("maxQuantityPerUser"),
                        "reservationTimeoutSeconds", config.get("reservationTimeoutSeconds")));
    }

    private String detailJson(long id) throws Exception {
        return mvc.perform(asAdmin(get("/api/admin/flash-sales/{id}", id), null))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Map<String, Object> detail(long id) throws Exception {
        return JsonPath.read(detailJson(id), "$");
    }

    private long itemIdOf(long saleId) {
        return jdbc.queryForObject("SELECT MIN(flash_sale_item_id) FROM flash_sale_items WHERE flash_sale_id = :id",
                Map.of("id", saleId), Long.class);
    }

    /** Chờ tới khi Oracle thấy giờ bắt đầu đã qua. */
    private void waitForOracleTime(long saleId) throws InterruptedException {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (jdbc.queryForObject("""
                SELECT COUNT(*) FROM flash_sales WHERE flash_sale_id = :id AND start_at <= SYSTIMESTAMP
                """, Map.of("id", saleId), Integer.class) == 0) {
            assertThat(System.nanoTime()).as("chờ giờ mở bán quá lâu").isLessThan(deadline);
            Thread.sleep(200);
        }
    }

    private long reserved(long productId) {
        return jdbc.queryForObject("SELECT reserved_flash_quantity FROM product_inventory WHERE product_id = :id",
                Map.of("id", productId), Long.class);
    }

    private long onHand(long productId) {
        return jdbc.queryForObject("SELECT on_hand_quantity FROM product_inventory WHERE product_id = :id",
                Map.of("id", productId), Long.class);
    }

    private int invariantViolations() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM v_inventory_invariant_check WHERE product_id IN (:ids)",
                Map.of("ids", PRODUCTS), Integer.class);
    }

    private String login(String email) {
        return auth.login(new LoginRequest(email, "ShopFlash123!")).accessToken();
    }

    private static String json(Object body) {
        return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(body);
    }

    private void insertProduct(long id, String name, long basePrice, String status, int onHand) {
        jdbc.update("""
                INSERT INTO products (product_id, category_id, sku, name, slug, description, base_price,
                                      thumbnail_url, status, created_at, updated_at)
                VALUES (:id, 9101, :sku, :name, :slug, NULL, :price, NULL, :status, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("id", id, "sku", "CCADM-" + id, "name", name, "slug", "ccadm-" + id, "price", basePrice,
                "status", status));
        jdbc.update("""
                INSERT INTO product_inventory (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
                VALUES (:id, :onHand, 0, 0, SYSTIMESTAMP)
                """, Map.of("id", id, "onHand", onHand));
    }

    /** Xóa mọi chương trình đụng tới sản phẩm test (kể cả lần chạy trước bị dừng giữa chừng). */
    private void removeSales() {
        List<Long> saleIds = jdbc.queryForList(
                "SELECT DISTINCT flash_sale_id FROM flash_sale_items WHERE product_id IN (:ids)",
                Map.of("ids", PRODUCTS), Long.class);
        List<Long> all = new ArrayList<>(saleIds);
        sales.stream().filter(s -> !all.contains(s)).forEach(all::add);
        for (long saleId : all) {
            List<Long> itemIds = jdbc.queryForList(
                    "SELECT flash_sale_item_id FROM flash_sale_items WHERE flash_sale_id = :id", Map.of("id", saleId),
                    Long.class);
            runtime.unloadSale(saleId, itemIds);
            if (!itemIds.isEmpty()) {
                List<Long> orderIds = jdbc.queryForList(
                        "SELECT order_id FROM order_items WHERE flash_sale_item_id IN (:ids)", Map.of("ids", itemIds),
                        Long.class);
                jdbc.update("DELETE FROM order_items WHERE flash_sale_item_id IN (:ids)", Map.of("ids", itemIds));
                if (!orderIds.isEmpty()) {
                    jdbc.update("DELETE FROM orders WHERE order_id IN (:ids)", Map.of("ids", orderIds));
                }
            }
            jdbc.update("DELETE FROM flash_sale_items WHERE flash_sale_id = :id", Map.of("id", saleId));
            jdbc.update("DELETE FROM flash_sale_configs WHERE flash_sale_id = :id", Map.of("id", saleId));
            jdbc.update("DELETE FROM flash_sales WHERE flash_sale_id = :id", Map.of("id", saleId));
        }
        sales.clear();
    }

    private void removeFixture() {
        removeSales();
        jdbc.update("DELETE FROM product_inventory WHERE product_id IN (:ids)", Map.of("ids", PRODUCTS));
        jdbc.update("DELETE FROM products WHERE product_id IN (:ids)", Map.of("ids", PRODUCTS));
    }
}
