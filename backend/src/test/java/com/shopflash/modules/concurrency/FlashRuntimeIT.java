package com.shopflash.modules.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.model.ClaimResult;
import com.shopflash.modules.concurrency.model.ItemRuntime;
import com.shopflash.modules.concurrency.model.ReleaseRule;
import com.shopflash.modules.concurrency.model.ReserveOutcome;
import com.shopflash.modules.concurrency.model.SaleRuntime;
import com.shopflash.modules.concurrency.repository.redis.FlashKeys;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;
import com.shopflash.modules.concurrency.service.FlashRuntimeService;

import redis.clients.jedis.RedisClient;

/**
 * Lua giữ suất trên Redis thật (và nạp runtime từ Oracle cho chương trình seed 9101).
 * Chạy trên stack Docker: ./mvnw test -Dtest='FlashRuntimeIT'
 * Dùng chương trình giả 990001-990004 chỉ có trên Redis; dọn sạch key sau khi chạy.
 */
// MOCK thay vì NONE: SecurityConfig của common/security cần môi trường web.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "shopflash.flash.jobs.enabled=false")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlashRuntimeIT {

    private static final long SALE = 990001;
    private static final long ONE_LEFT = 990011;   // 1 suất
    private static final long PLENTY = 990012;     // 50 suất
    private static final long CONTESTED = 990013;  // 30 suất cho 200 người tranh

    @Autowired
    FlashRuntimeRepository runtime;
    @Autowired
    FlashRuntimeService runtimeService;
    @Autowired
    RedisClient redis;
    @Autowired
    NamedParameterJdbcTemplate jdbc;

    private final Set<String> reservations = ConcurrentHashMap.newKeySet();
    private long now;

    @BeforeEach
    void freshRuntime() {
        now = runtime.serverTimeMs();
        // Nạp lại mỗi test: reservation cũ bị bỏ, suất trở về đầy đủ.
        runtime.loadSale(new SaleRuntime(SALE, "ACTIVE", now - 60_000, now + 3_600_000, 2, 60), List.of(
                item(ONE_LEFT, 1), item(PLENTY, 50), item(CONTESTED, 30)));
    }

    @AfterAll
    void cleanUp() {
        runtime.unloadSale(SALE, List.of(ONE_LEFT, PLENTY, CONTESTED));
        runtime.unloadSale(990002, List.of(990021L));
        runtime.unloadSale(990003, List.of(990031L));
        runtime.unloadSale(990004, List.of(990041L));
        for (String id : reservations) {
            redis.unlink(FlashKeys.reservation(id));
            redis.zrem(FlashKeys.EXPIRY, id);
        }
    }

    @Test
    void reserveTakesOneSlotAndRepeatedClickReturnsTheSameReservation() {
        ReserveOutcome first = reserve(PLENTY, 1, 1);
        assertThat(first.created()).isTrue();
        assertThat(first.expiresAtMs()).isGreaterThan(first.serverTimeMs());
        assertThat(remaining(PLENTY)).isEqualTo(49);
        assertThat(redis.hget(FlashKeys.userQty(PLENTY), "1")).isEqualTo("1");

        ReserveOutcome again = reserve(PLENTY, 1, 1);
        assertThat(again.created()).isFalse();
        assertThat(again.reservationId()).isEqualTo(first.reservationId());
        assertThat(remaining(PLENTY)).isEqualTo(49);
    }

    @Test
    void soldOutAndPerUserLimitAreCheckedInsideTheScript() {
        reserve(ONE_LEFT, 1, 1);
        assertThat(errorOf(() -> reserve(ONE_LEFT, 2, 1))).isEqualTo("SOLD_OUT");
        // Giới hạn 2 mỗi người, giữ 3 thì bị từ chối dù còn 50 suất.
        assertThat(errorOf(() -> reserve(PLENTY, 3, 3))).isEqualTo("USER_LIMIT_REACHED");
        assertThat(remaining(PLENTY)).isEqualTo(50);
    }

    @Test
    void saleWindowFollowsTheRedisClock() {
        runtime.loadSale(new SaleRuntime(990003, "SCHEDULED", now + 3_600_000, now + 7_200_000, 1, 60),
                List.of(new ItemRuntime(990031, 9101, 5, 0, "ACTIVE", Map.of())));
        runtime.loadSale(new SaleRuntime(990004, "ACTIVE", now - 7_200_000, now - 60_000, 1, 60),
                List.of(new ItemRuntime(990041, 9101, 5, 0, "ACTIVE", Map.of())));
        assertThat(errorOf(() -> runtime.reserve(990003, 990031, 1, 1))).isEqualTo("SALE_NOT_STARTED");
        assertThat(errorOf(() -> runtime.reserve(990004, 990041, 1, 1))).isEqualTo("SALE_ENDED");
        assertThat(errorOf(() -> runtime.reserve(SALE, 990031, 1, 1))).isEqualTo("ITEM_UNAVAILABLE");
    }

    @Test
    void claimThenConfirmKeepsTheSlotSold() {
        ReserveOutcome r = reserve(PLENTY, 4, 2);
        ClaimResult claim = runtime.claim(r.reservationId(), 4);
        assertThat(claim.alreadyConfirmed()).isFalse();
        assertThat(claim.reservation().itemId()).isEqualTo(PLENTY);
        assertThat(claim.reservation().quantity()).isEqualTo(2);
        // Gửi đặt đơn lần hai trong lúc lần đầu chưa xong.
        assertThat(errorOf(() -> runtime.claim(r.reservationId(), 4))).isEqualTo("RESERVATION_ALREADY_USED");
        // Người khác không thấy reservation này.
        assertThat(errorOf(() -> runtime.claim(r.reservationId(), 5))).isEqualTo("RESERVATION_NOT_FOUND");

        assertThat(runtime.confirm(r.reservationId(), PLENTY)).isTrue();
        assertThat(runtime.claim(r.reservationId(), 4).alreadyConfirmed()).isTrue();
        assertThat(runtime.release(r.reservationId(), ReleaseRule.rollback())).isFalse();
        assertThat(remaining(PLENTY)).isEqualTo(48);
        assertThat(redis.hget(FlashKeys.userQty(PLENTY), "4")).isEqualTo("2");
        assertThat(redis.hexists(FlashKeys.pending(PLENTY), "4")).isFalse();
    }

    @Test
    void compensationReturnsTheSlotExactlyOnce() {
        ReserveOutcome r = reserve(PLENTY, 6, 2);
        runtime.claim(r.reservationId(), 6);
        // Hook rollback và job dọn cùng gọi: chỉ một lần có tác dụng.
        assertThat(runtime.release(r.reservationId(), ReleaseRule.rollback())).isTrue();
        assertThat(runtime.release(r.reservationId(), ReleaseRule.rollback())).isFalse();
        assertThat(runtime.release(r.reservationId(), ReleaseRule.staleClaim(0))).isFalse();
        assertThat(remaining(PLENTY)).isEqualTo(50);
        assertThat(redis.hexists(FlashKeys.userQty(PLENTY), "6")).isFalse();
        assertThat(runtime.find(r.reservationId()).orElseThrow().status()).isEqualTo("RELEASED");
    }

    @Test
    void onlyTheOwnerCanCancelAPendingReservation() {
        ReserveOutcome r = reserve(PLENTY, 7, 1);
        assertThat(runtime.release(r.reservationId(), ReleaseRule.cancelledBy(8))).isFalse();
        assertThat(runtime.release(r.reservationId(), ReleaseRule.cancelledBy(7))).isTrue();
        assertThat(remaining(PLENTY)).isEqualTo(50);
    }

    @Test
    void expiredPendingReservationIsReleasedOnlyAfterItsDeadline() throws InterruptedException {
        runtime.loadSale(new SaleRuntime(990002, "ACTIVE", now - 60_000, now + 3_600_000, 1, 1),
                List.of(new ItemRuntime(990021, 9101, 5, 0, "ACTIVE", Map.of())));
        ReserveOutcome r = runtime.reserve(990002, 990021, 9, 1);
        reservations.add(r.reservationId());
        assertThat(runtime.release(r.reservationId(), ReleaseRule.expiredPending())).isFalse();
        assertThat(runtime.dueReservations(runtime.serverTimeMs(), 100)).doesNotContain(r.reservationId());

        Thread.sleep(1_200);
        assertThat(runtime.dueReservations(runtime.serverTimeMs(), 100)).contains(r.reservationId());
        assertThat(runtime.release(r.reservationId(), ReleaseRule.expiredPending())).isTrue();
        assertThat(remaining(990021)).isEqualTo(5);
        assertThat(errorOf(() -> runtime.claim(r.reservationId(), 9))).isEqualTo("RESERVATION_EXPIRED");
    }

    @Test
    void twoHundredBuyersNeverTakeMoreThanThirtySlots() throws Exception {
        Map<String, Integer> outcomes = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        // Platform thread: virtual thread bị ghim trong pool của Jedis trên JDK 21–23 (xem LabRunner).
        try (ExecutorService pool = Executors.newFixedThreadPool(200)) {
            for (int i = 0; i < 200; i++) {
                long userId = 1000 + i;
                tasks.add(pool.submit(() -> {
                    start.await();
                    String result;
                    try {
                        result = reserve(CONTESTED, userId, 1).created() ? "CREATED" : "EXISTING";
                    } catch (ApiException e) {
                        result = e.code();
                    }
                    outcomes.merge(result, 1, Integer::sum);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        }
        assertThat(outcomes).containsEntry("CREATED", 30).containsEntry("SOLD_OUT", 170).hasSize(2);
        assertThat(remaining(CONTESTED)).isZero();
        assertThat(redis.hlen(FlashKeys.pending(CONTESTED))).isEqualTo(30);
    }

    @Test
    void doubleClickFromOneUserCreatesOneReservation() throws Exception {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        Map<Boolean, Integer> created = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 20; i++) {
                tasks.add(pool.submit(() -> {
                    start.await();
                    ReserveOutcome r = reserve(PLENTY, 42, 1);
                    ids.add(r.reservationId());
                    created.merge(r.created(), 1, Integer::sum);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        }
        assertThat(ids).hasSize(1);
        assertThat(created).containsEntry(true, 1).containsEntry(false, 19);
        assertThat(remaining(PLENTY)).isEqualTo(49);
    }

    @Test
    void reloadingDropsPendingReservationsAndReopensTheirSlots() {
        ReserveOutcome r = reserve(PLENTY, 11, 2);
        assertThat(remaining(PLENTY)).isEqualTo(48);
        long dropped = runtime.loadSale(new SaleRuntime(SALE, "ACTIVE", now - 60_000, now + 3_600_000, 2, 60),
                List.of(item(PLENTY, 50)));
        assertThat(dropped).isEqualTo(1);
        assertThat(remaining(PLENTY)).isEqualTo(50);
        assertThat(runtime.find(r.reservationId()).orElseThrow().status()).isEqualTo("RELEASED");
        assertThat(errorOf(() -> runtime.claim(r.reservationId(), 11))).isEqualTo("RESERVATION_EXPIRED");
    }

    @Test
    void seedSaleIsRebuiltFromOracle() {
        runtimeService.load(9101);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT flash_sale_item_id, allocated_quantity - sold_quantity AS remaining
                FROM flash_sale_items WHERE flash_sale_id = 9101
                """, Map.of());
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> row : rows) {
            long itemId = ((Number) row.get("FLASH_SALE_ITEM_ID")).longValue();
            long expected = ((Number) row.get("REMAINING")).longValue();
            assertThat(remaining(itemId)).as("item %d", itemId).isEqualTo(expected);
        }
        assertThat(redis.hget(FlashKeys.sale(9101), "status")).isEqualTo("ACTIVE");
    }

    private ReserveOutcome reserve(long itemId, long userId, int quantity) {
        ReserveOutcome r = runtime.reserve(SALE, itemId, userId, quantity);
        reservations.add(r.reservationId());
        return r;
    }

    private long remaining(long itemId) {
        return runtime.remaining(List.of(itemId)).get(itemId);
    }

    private static ItemRuntime item(long itemId, int allocated) {
        return new ItemRuntime(itemId, 9101, allocated, 0, "ACTIVE", Map.of());
    }

    private static String errorOf(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e.code();
        }
        return "NO_ERROR";
    }
}
