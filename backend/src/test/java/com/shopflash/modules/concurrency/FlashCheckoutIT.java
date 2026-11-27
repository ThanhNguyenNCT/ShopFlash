package com.shopflash.modules.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.shopflash.common.security.AuthService;
import com.shopflash.common.security.LoginRequest;
import com.shopflash.modules.concurrency.model.ClaimedReservation;
import com.shopflash.modules.concurrency.model.ReservationView;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository.NewOrder;
import com.shopflash.modules.concurrency.repository.oracle.FlashOrderRepository.OrderLine;
import com.shopflash.modules.concurrency.repository.redis.FlashKeys;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;
import com.shopflash.modules.concurrency.service.FlashCheckoutService;
import com.shopflash.modules.concurrency.service.FlashRuntimeService;
import com.shopflash.modules.concurrency.service.ReservationSweeper;
import com.shopflash.modules.concurrency.service.ReservationSweeper.SweepResult;
import com.shopflash.modules.storage.repository.ProductProjectionRepository;
import com.shopflash.modules.storage.repository.StorageRepository;
import com.shopflash.modules.storage.service.ProjectionConsistencyChecker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import redis.clients.jedis.RedisClient;

/**
 * F9 phía khách qua HTTP trên Oracle và Redis thật: ./mvnw test -Dtest='FlashCheckoutIT'
 * Dựng chương trình riêng 990201 (sản phẩm 990201, 5 suất, mỗi người 2, giữ suất 3 s) và xóa sạch sau khi chạy.
 * Cần khách 9001-9003 và admin của seed dev. Job định kỳ tắt; test tự gọi sweeper.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "shopflash.flash.jobs.enabled=false",
        "shopflash.flash.claim-grace-ms=500" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlashCheckoutIT {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("flash-checkout-test-secret-32-bytes!!".getBytes());
    private static final long SALE = 990201;
    private static final long ITEM = 990201;
    private static final long PRODUCT = 990201;
    private static final int ALLOCATED = 5;
    private static final int ON_HAND = 20;
    private static final BigDecimal PRICE = new BigDecimal("199000");
    private static final String BODY = """
            {"recipientName": "Nguyễn Văn A", "recipientPhone": "0900000000", "shippingAddress": "1 Lý Thường Kiệt"}
            """;

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RedisClient redis;
    @Autowired FlashRuntimeRepository runtime;
    @Autowired FlashRuntimeService runtimeService;
    @Autowired FlashOrderRepository orders;
    @Autowired ReservationSweeper sweeper;
    @Autowired AuthService auth;
    @Autowired WebApplicationContext webContext;
    @Autowired @Qualifier("springSecurityFilterChain") Filter securityFilter;
    @Autowired ProductProjectionRepository projection;
    @Autowired StorageRepository storage;
    @Autowired ObjectMapper json;

    private final Set<String> reservations = ConcurrentHashMap.newKeySet();
    private MockMvc mvc;
    private String alice;
    private String bob;
    private String carol;
    private String admin;

    @BeforeAll
    void createSale() {
        removeFixture();
        jdbc.update("""
                INSERT INTO products (product_id, category_id, sku, name, slug, description, base_price,
                                      thumbnail_url, status, created_at, updated_at)
                VALUES (:id, 9101, 'CCIT-990201', 'Sản phẩm test flash', 'ccit-990201', NULL, 500000, NULL,
                        'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("id", PRODUCT));
        jdbc.update("""
                INSERT INTO product_inventory (product_id, on_hand_quantity, reserved_flash_quantity, version, updated_at)
                VALUES (:id, :onHand, :reserved, 0, SYSTIMESTAMP)
                """, Map.of("id", PRODUCT, "onHand", ON_HAND, "reserved", ALLOCATED));
        jdbc.update("""
                INSERT INTO flash_sales (flash_sale_id, name, description, start_at, end_at, status, version,
                                         created_at, updated_at)
                VALUES (:id, 'Flash test', NULL, SYSTIMESTAMP - INTERVAL '1' HOUR, SYSTIMESTAMP + INTERVAL '1' HOUR,
                        'ACTIVE', 0, SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("id", SALE));
        jdbc.update("""
                INSERT INTO flash_sale_configs (flash_sale_id, max_quantity_per_user, reservation_timeout_seconds,
                                                allow_cancel, created_at, updated_at)
                VALUES (:id, 2, 3, 'N', SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("id", SALE));
        jdbc.update("""
                INSERT INTO flash_sale_items (flash_sale_item_id, flash_sale_id, product_id, sale_price,
                                              allocated_quantity, sold_quantity, status, created_at, updated_at)
                VALUES (:item, :sale, :product, :price, :allocated, 0, 'ACTIVE', SYSTIMESTAMP, SYSTIMESTAMP)
                """, Map.of("item", ITEM, "sale", SALE, "product", PRODUCT, "price", PRICE, "allocated", ALLOCATED));

        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilter).build();
        alice = login("cc001@shopflash.local");
        bob = login("cc002@shopflash.local");
        carol = login("cc003@shopflash.local");
        admin = login("admin@shopflash.local");
    }

    @BeforeEach
    void freshState() {
        deleteOrders();
        jdbc.update("UPDATE flash_sale_items SET sold_quantity = 0 WHERE flash_sale_item_id = :id", Map.of("id", ITEM));
        jdbc.update("""
                UPDATE product_inventory SET on_hand_quantity = :onHand, reserved_flash_quantity = :reserved
                WHERE product_id = :id
                """, Map.of("id", PRODUCT, "onHand", ON_HAND, "reserved", ALLOCATED));
        // Nạp lại runtime: reservation cũ bị bỏ, remaining = allocated - sold = 5.
        runtimeService.load(SALE);
    }

    @AfterAll
    void cleanUp() {
        removeFixture();
        redis.unlink(ProductProjectionRepository.key(PRODUCT));
        for (String id : reservations) {
            redis.unlink(FlashKeys.reservation(id));
            redis.zrem(FlashKeys.EXPIRY, id);
        }
    }

    @Test
    void anyoneSeesTheLiveSaleAndItsStock() throws Exception {
        MvcResult active = mvc.perform(get("/api/flash-sales/active"))
                .andExpect(status().isOk())
                .andReturn();
        String json = active.getResponse().getContentAsString();
        List<Map<String, Object>> sale = JsonPath.read(json, "$.sales[?(@.flashSaleId == " + SALE + ")]");
        assertThat(sale).hasSize(1);
        assertThat(sale.getFirst().get("phase")).isEqualTo("LIVE");
        assertThat(JsonPath.<List<Integer>>read(json,
                "$.sales[?(@.flashSaleId == " + SALE + ")].items[0].remaining")).containsExactly(ALLOCATED);
        assertThat(JsonPath.<String>read(json, "$.serverTime")).endsWith("+07:00");

        mvc.perform(get("/api/flash-sales/{id}/stock", SALE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemId").value(ITEM))
                .andExpect(jsonPath("$.items[0].remaining").value(ALLOCATED));
        mvc.perform(get("/api/flash-sales/{id}/stock", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FLASH_SALE_NOT_FOUND"));
    }

    @Test
    void reserveThenCheckoutWritesOneFlashOrder() throws Exception {
        long versionBefore = inventory().get("VERSION");
        String id = reserve(alice, 2, 201);
        mvc.perform(reservePost(alice, 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(id))
                .andExpect(jsonPath("$.totalAmount").value(398000));
        assertThat(remaining()).isEqualTo(3);

        MvcResult placed = mvc.perform(checkoutPost(alice, id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(398000))
                .andReturn();
        long orderId = ((Number) JsonPath.read(placed.getResponse().getContentAsString(), "$.orderId")).longValue();
        assertThat(JsonPath.<String>read(placed.getResponse().getContentAsString(), "$.orderCode")).startsWith("FS-");

        // Oracle: sold, tồn kho, đơn và dòng đơn.
        assertThat(sold()).isEqualTo(2);
        Map<String, Long> inv = inventory();
        assertThat(inv.get("ON_HAND_QUANTITY")).isEqualTo(ON_HAND - 2);
        assertThat(inv.get("RESERVED_FLASH_QUANTITY")).isEqualTo(ALLOCATED - 2);
        assertThat(inv.get("VERSION")).isEqualTo(versionBefore + 1);
        Map<String, Object> order = jdbc.queryForMap("""
                SELECT o.order_type, o.user_id, o.total_amount, oi.flash_sale_item_id, oi.quantity, oi.line_total
                FROM orders o JOIN order_items oi ON oi.order_id = o.order_id
                WHERE o.idempotency_key = :key
                """, Map.of("key", FlashCheckoutService.idempotencyKey(id)));
        assertThat(order.get("ORDER_TYPE")).isEqualTo("FLASH_SALE");
        assertThat(((Number) order.get("USER_ID")).longValue()).isEqualTo(9001);
        assertThat(((Number) order.get("FLASH_SALE_ITEM_ID")).longValue()).isEqualTo(ITEM);
        assertThat(((BigDecimal) order.get("LINE_TOTAL"))).isEqualByComparingTo("398000");
        assertThat(invariantViolations()).isZero();

        // Redis: reservation CONFIRMED, suất không bị trả lại.
        assertThat(reservation(id).status()).isEqualTo("CONFIRMED");
        assertThat(remaining()).isEqualTo(3);

        // Gửi lại cùng reservation (mất phản hồi): trả đơn cũ, không ghi thêm.
        mvc.perform(checkoutPost(alice, id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId));
        assertThat(sold()).isEqualTo(2);
        // Đã mua đủ 2 (giới hạn mỗi người).
        mvc.perform(reservePost(alice, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("USER_LIMIT_REACHED"));
    }

    @Test
    void flashCheckoutRefreshesProjectionWithNormalAvailableStock() throws Exception {
        // Còn 5 trong kho nhưng cả 5 đang giữ cho flash sale: bán thường hết hàng dù tổng tồn > 0.
        jdbc.update("UPDATE product_inventory SET on_hand_quantity = :q WHERE product_id = :id",
                Map.of("q", ALLOCATED, "id", PRODUCT));
        projection.upsert(storage.findProduct(PRODUCT));
        String key = ProductProjectionRepository.key(PRODUCT);
        redis.sendCommand(() -> "JSON.SET".getBytes(), key, "$.inStock", "true");
        redis.sendCommand(() -> "JSON.SET".getBytes(), key, "$.name", "\"STALE\"");

        String id = reserve(alice, 1, 201);
        mvc.perform(checkoutPost(alice, id)).andExpect(status().isCreated());

        Map<String, Long> inv = inventory();
        assertThat(inv.get("ON_HAND_QUANTITY")).isEqualTo(ALLOCATED - 1);
        assertThat(inv.get("RESERVED_FLASH_QUANTITY")).isEqualTo(ALLOCATED - 1);
        JsonNode doc = json.readTree(projection.read(List.of(key)).getFirst().json());
        assertThat(doc.get("inStock").booleanValue()).isFalse();
        assertThat(ProjectionConsistencyChecker.diff(ProductProjectionRepository.fields(storage.findProduct(PRODUCT)), doc))
                .isEmpty();
        assertThat(reservation(id).status()).isEqualTo("CONFIRMED");
    }

    @Test
    void manyCheckoutsOfOneReservationCreateOneOrder() throws Exception {
        String id = reserve(bob, 1, 201);
        int threads = 8;
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                statuses.add(pool.submit(() -> {
                    gate.await();
                    return mvc.perform(checkoutPost(bob, id)).andReturn().getResponse().getStatus();
                }));
            }
            gate.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : statuses) {
                codes.add(f.get());
            }
            assertThat(codes).isSubsetOf(201, 200, 409);
            assertThat(codes).containsOnlyOnce(201);
        }
        Integer orderCount = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE idempotency_key = :key",
                Map.of("key", FlashCheckoutService.idempotencyKey(id)), Integer.class);
        assertThat(orderCount).isEqualTo(1);
        assertThat(sold()).isEqualTo(1);
        assertThat(invariantViolations()).isZero();
    }

    @Test
    void oracleFailureRollsBackAndReturnsTheSlot() throws Exception {
        String id = reserve(carol, 1, 201);
        assertThat(remaining()).isEqualTo(ALLOCATED - 1);
        // Làm câu xuất kho thất bại (reserved_flash = 0): transaction phải rollback cả câu tăng sold.
        jdbc.update("UPDATE product_inventory SET reserved_flash_quantity = 0 WHERE product_id = :id",
                Map.of("id", PRODUCT));

        mvc.perform(checkoutPost(carol, id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SOLD_OUT"));

        assertThat(sold()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE idempotency_key = :key",
                Map.of("key", FlashCheckoutService.idempotencyKey(id)), Integer.class)).isZero();
        // Bù trừ: suất về lại Redis, lượt đã giữ của người dùng được trừ.
        assertThat(reservation(id).status()).isEqualTo("RELEASED");
        assertThat(remaining()).isEqualTo(ALLOCATED);
        assertThat(redis.hget(FlashKeys.userQty(ITEM), "9003")).isNull();
    }

    @Test
    void customerCancelsOwnPendingReservation() throws Exception {
        String id = reserve(alice, 1, 201);
        mvc.perform(delete("/api/flash-sales/reservations/{id}", id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/flash-sales/reservations/{id}", id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/flash-sales/reservations/{id}", id).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
        assertThat(remaining()).isEqualTo(ALLOCATED);
        mvc.perform(delete("/api/flash-sales/reservations/{id}", id).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
        mvc.perform(checkoutPost(alice, id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESERVATION_EXPIRED"));
    }

    @Test
    void confirmedReservationCannotBeCancelled() throws Exception {
        String id = reserve(bob, 1, 201);
        mvc.perform(checkoutPost(bob, id)).andExpect(status().isCreated());
        mvc.perform(delete("/api/flash-sales/reservations/{id}", id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESERVATION_ALREADY_USED"));
        mvc.perform(get("/api/flash-sales/reservations/{id}", id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void expiredReservationIsReturnedBySweeper() throws Exception {
        String id = reserve(alice, 1, 201);
        waitUntil(reservation(id).expiresAtMs() + 50);

        mvc.perform(checkoutPost(alice, id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESERVATION_EXPIRED"));
        SweepResult result = sweeper.sweep();

        assertThat(result.released()).isGreaterThanOrEqualTo(1);
        assertThat(reservation(id).status()).isEqualTo("RELEASED");
        assertThat(remaining()).isEqualTo(ALLOCATED);
    }

    @Test
    void sweeperResolvesStaleClaimsFromOracle() throws Exception {
        sweeper.sweep(); // dọn reservation cũ còn sót của chương trình khác để số đếm bên dưới chỉ là của test này
        // Hai lần đặt đơn bị sập sau bước claim: A kịp commit đơn (mất hook confirm), B chưa ghi gì.
        String a = reserve(alice, 1, 201);
        String b = reserve(bob, 1, 201);
        ClaimedReservation claimedA = runtime.claim(a, 9001).reservation();
        runtime.claim(b, 9002);
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            orders.addSold(ITEM, 1);
            OrderLine line = orders.orderLine(ITEM).orElseThrow();
            orders.takeFlashStock(PRODUCT, 1);
            long orderId = orders.insertOrder(new NewOrder("FS-TEST-" + a.substring(0, 8),
                    FlashCheckoutService.idempotencyKey(a), 9001, PRICE, "A", "0900000000", "HCM"));
            orders.insertOrderItem(orderId, claimedA.itemId(), line, 1);
        });

        SweepResult early = sweeper.sweep();
        assertThat(early.confirmed() + early.released()).as("chưa quá hạn thì chưa đụng").isZero();

        waitUntil(Math.max(reservation(a).expiresAtMs(), reservation(b).expiresAtMs()) + 600);
        SweepResult result = sweeper.sweep();

        assertThat(result.confirmed()).isEqualTo(1);
        assertThat(result.released()).isEqualTo(1);
        assertThat(reservation(a).status()).isEqualTo("CONFIRMED");
        assertThat(reservation(b).status()).isEqualTo("RELEASED");
        // Redis khớp Oracle: remaining = allocated - sold.
        assertThat(remaining()).isEqualTo(ALLOCATED - sold());
        assertThat(invariantViolations()).isZero();
    }

    @Test
    void onlyCustomersCanReserveAndInputIsValidated() throws Exception {
        mvc.perform(post("/api/flash-sales/{s}/items/{i}/reservations", SALE, ITEM))
                .andExpect(status().isUnauthorized());
        mvc.perform(reservePost(admin, 1)).andExpect(status().isForbidden());
        mvc.perform(reservePost(alice, 0)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/flash-sales/{s}/items/{i}/reservations", SALE + 1, ITEM)
                .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FLASH_SALE_NOT_FOUND"));
        mvc.perform(get("/api/flash-sales/reservations/{id}", "not-a-uuid").header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound());

        String id = reserve(alice, 1, 201);
        mvc.perform(post("/api/flash-sales/reservations/{id}/checkout", id)
                .header("Authorization", "Bearer " + alice).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientName\": \"  \", \"recipientPhone\": \"09\", \"shippingAddress\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.field").value("recipientName"));
        // Lỗi dữ liệu nhận hàng xảy ra trước bước claim nên suất vẫn PENDING.
        assertThat(reservation(id).status()).isEqualTo("PENDING");
    }

    // ---- helpers ----

    private String reserve(String token, int quantity, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(reservePost(token, quantity))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.reservationId");
        reservations.add(id);
        return id;
    }

    private org.springframework.test.web.servlet.RequestBuilder reservePost(String token, int quantity) {
        return post("/api/flash-sales/{s}/items/{i}/reservations", SALE, ITEM)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": " + quantity + "}");
    }

    private org.springframework.test.web.servlet.RequestBuilder checkoutPost(String token, String id) {
        return post("/api/flash-sales/reservations/{id}/checkout", id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    private ReservationView reservation(String id) {
        return runtime.find(id).orElseThrow();
    }

    private long remaining() {
        return runtime.remaining(List.of(ITEM)).get(ITEM);
    }

    private long sold() {
        return jdbc.queryForObject("SELECT sold_quantity FROM flash_sale_items WHERE flash_sale_item_id = :id",
                Map.of("id", ITEM), Long.class);
    }

    private Map<String, Long> inventory() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT on_hand_quantity, reserved_flash_quantity, version FROM product_inventory WHERE product_id = :id
                """, Map.of("id", PRODUCT));
        return Map.of("ON_HAND_QUANTITY", ((Number) row.get("ON_HAND_QUANTITY")).longValue(),
                "RESERVED_FLASH_QUANTITY", ((Number) row.get("RESERVED_FLASH_QUANTITY")).longValue(),
                "VERSION", ((Number) row.get("VERSION")).longValue());
    }

    private int invariantViolations() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM v_inventory_invariant_check WHERE product_id = :id",
                Map.of("id", PRODUCT), Integer.class);
    }

    /** Chờ tới khi đồng hồ Redis qua mốc epochMs (giờ hết hạn tính theo đồng hồ Redis). */
    private void waitUntil(long epochMs) throws InterruptedException {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (runtime.serverTimeMs() < epochMs) {
            assertThat(System.nanoTime()).as("chờ quá lâu").isLessThan(deadline);
            Thread.sleep(100);
        }
    }

    private String login(String email) {
        return auth.login(new LoginRequest(email, "ShopFlash123!")).accessToken();
    }

    private void deleteOrders() {
        List<Long> orderIds = jdbc.queryForList(
                "SELECT order_id FROM order_items WHERE flash_sale_item_id = :id", Map.of("id", ITEM), Long.class);
        jdbc.update("DELETE FROM order_items WHERE flash_sale_item_id = :id", Map.of("id", ITEM));
        if (!orderIds.isEmpty()) {
            jdbc.update("DELETE FROM orders WHERE order_id IN (:ids)", Map.of("ids", orderIds));
        }
    }

    private void removeFixture() {
        deleteOrders();
        jdbc.update("DELETE FROM flash_sale_items WHERE flash_sale_item_id = :id", Map.of("id", ITEM));
        jdbc.update("DELETE FROM flash_sale_configs WHERE flash_sale_id = :id", Map.of("id", SALE));
        jdbc.update("DELETE FROM flash_sales WHERE flash_sale_id = :id", Map.of("id", SALE));
        jdbc.update("DELETE FROM product_inventory WHERE product_id = :id", Map.of("id", PRODUCT));
        jdbc.update("DELETE FROM products WHERE product_id = :id", Map.of("id", PRODUCT));
        runtime.unloadSale(SALE, List.of(ITEM));
    }
}
