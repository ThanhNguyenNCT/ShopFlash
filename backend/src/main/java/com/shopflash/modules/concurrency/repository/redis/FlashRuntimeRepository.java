package com.shopflash.modules.concurrency.repository.redis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.model.ClaimResult;
import com.shopflash.modules.concurrency.model.ClaimedReservation;
import com.shopflash.modules.concurrency.model.ItemRuntime;
import com.shopflash.modules.concurrency.model.ReleaseRule;
import com.shopflash.modules.concurrency.model.ReservationView;
import com.shopflash.modules.concurrency.model.ReserveOutcome;
import com.shopflash.modules.concurrency.model.SaleRuntime;
import com.shopflash.modules.concurrency.repository.redis.LuaScripts.Script;

import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.Response;

/**
 * Runtime flash sale trên Redis: suất còn lại, số lượng mỗi người đã giữ, reservation.
 * Mọi thao tác ghi đi qua Lua script để kiểm tra và ghi trong cùng một bước nguyên tử.
 */
@Repository
public class FlashRuntimeRepository {

    private final RedisClient redis;
    private final LuaScripts scripts;

    public FlashRuntimeRepository(RedisClient redis, LuaScripts scripts) {
        this.redis = redis;
        this.scripts = scripts;
    }

    /** Giữ suất. Lỗi nghiệp vụ (hết suất, quá giới hạn, chương trình chưa mở...) ném ApiException. */
    public ReserveOutcome reserve(long saleId, long itemId, long userId, int quantity) {
        String newId = UUID.randomUUID().toString();
        List<Object> reply = list(scripts.run(Script.RESERVE,
                List.of(FlashKeys.sale(saleId), FlashKeys.item(itemId), FlashKeys.userQty(itemId),
                        FlashKeys.pending(itemId), FlashKeys.reservation(newId), FlashKeys.EXPIRY),
                List.of(String.valueOf(userId), String.valueOf(quantity), newId, String.valueOf(saleId),
                        String.valueOf(itemId))));
        String code = (String) reply.getFirst();
        if (!"CREATED".equals(code) && !"EXISTING".equals(code)) {
            throw FlashErrors.fromScript(code);
        }
        return new ReserveOutcome("CREATED".equals(code), (String) reply.get(1), asLong(reply.get(2)),
                asLong(reply.get(3)));
    }

    /** PENDING -> CLAIMED trước khi ghi đơn. */
    public ClaimResult claim(String reservationId, long userId) {
        List<Object> reply = list(scripts.run(Script.CLAIM, List.of(FlashKeys.reservation(reservationId)),
                List.of(String.valueOf(userId))));
        String code = (String) reply.getFirst();
        return switch (code) {
            case "CLAIMED" -> ClaimResult.claimed(new ClaimedReservation(reservationId, asLong(reply.get(1)),
                    asLong(reply.get(2)), asLong(reply.get(3)), userId, (int) asLong(reply.get(4))));
            case "CONFIRMED" -> ClaimResult.confirmedBefore();
            default -> throw FlashErrors.fromScript(code);
        };
    }

    /** Oracle đã commit: CLAIMED -> CONFIRMED. Trả false khi reservation không còn ở CLAIMED. */
    public boolean confirm(String reservationId, long itemId) {
        Object reply = scripts.run(Script.CONFIRM,
                List.of(FlashKeys.reservation(reservationId), FlashKeys.pending(itemId), FlashKeys.EXPIRY),
                List.of(reservationId));
        return asLong(reply) == 1;
    }

    /** Trả suất theo quy tắc; gọi trùng cũng chỉ trả một lần. Trả false khi không đủ điều kiện. */
    public boolean release(String reservationId, ReleaseRule rule) {
        String itemField = hget(FlashKeys.reservation(reservationId), "itemId");
        if (itemField == null) {
            return false;
        }
        long itemId = Long.parseLong(itemField);
        Object reply = scripts.run(Script.RELEASE,
                List.of(FlashKeys.reservation(reservationId), FlashKeys.item(itemId), FlashKeys.userQty(itemId),
                        FlashKeys.pending(itemId), FlashKeys.EXPIRY),
                List.of(reservationId, rule.allowedStatuses(), String.valueOf(rule.graceMs()),
                        rule.ownerUserId() == null ? "" : String.valueOf(rule.ownerUserId())));
        return asLong(reply) == 1;
    }

    public Optional<ReservationView> find(String reservationId) {
        List<String> f = call(() -> redis.hmget(FlashKeys.reservation(reservationId), "saleId", "itemId",
                "productId", "userId", "quantity", "status", "createdAt", "expiresAt"));
        if (f.get(5) == null) {
            return Optional.empty();
        }
        return Optional.of(new ReservationView(reservationId, Long.parseLong(f.get(0)), Long.parseLong(f.get(1)),
                Long.parseLong(f.get(2)), Long.parseLong(f.get(3)), Integer.parseInt(f.get(4)), f.get(5),
                Long.parseLong(f.get(6)), Long.parseLong(f.get(7))));
    }

    /** Suất còn lại của nhiều sản phẩm trong một round-trip. Sản phẩm chưa nạp runtime thì không có trong map. */
    public Map<Long, Long> remaining(Collection<Long> itemIds) {
        return call(() -> {
            Map<Long, Response<String>> responses = new LinkedHashMap<>();
            try (AbstractPipeline p = redis.pipelined()) {
                for (Long itemId : itemIds) {
                    responses.put(itemId, p.hget(FlashKeys.item(itemId), "remaining"));
                }
                p.sync();
            }
            Map<Long, Long> out = new LinkedHashMap<>();
            responses.forEach((itemId, r) -> {
                if (r.get() != null) {
                    out.put(itemId, Long.parseLong(r.get()));
                }
            });
            return out;
        });
    }

    /** Reservation có expiresAt <= uptoMs, theo thứ tự hết hạn, tối đa limit cái (cho job dọn). */
    public List<String> dueReservations(long uptoMs, int limit) {
        return call(() -> redis.zrangeByScore(FlashKeys.EXPIRY, Double.NEGATIVE_INFINITY, uptoMs, 0, limit));
    }

    /** Giờ hiện tại theo đồng hồ Redis (cùng đồng hồ mà Lua dùng để mở, đóng chương trình). */
    public long serverTimeMs() {
        List<Object> t = list(call(() -> redis.sendCommand(Protocol.Command.TIME)));
        long seconds = Long.parseLong(text(t.get(0)));
        long micros = Long.parseLong(text(t.get(1)));
        return seconds * 1000 + micros / 1000;
    }

    /** Danh sách item của chương trình đã nạp runtime; empty nếu chương trình chưa nạp. */
    public Optional<List<Long>> saleItemIds(long saleId) {
        String csv = hget(FlashKeys.sale(saleId), "itemIds");
        if (csv == null) {
            return Optional.empty();
        }
        return Optional.of(csv.isEmpty() ? List.of()
                : java.util.Arrays.stream(csv.split(",")).map(Long::parseLong).toList());
    }

    /** Bỏ reservation khỏi hàng đợi hết hạn (key reservation đã mất hoặc đã kết thúc). */
    public void forgetDue(String reservationId) {
        call(() -> redis.zrem(FlashKeys.EXPIRY, reservationId));
    }

    public boolean saleLoaded(long saleId) {
        return call(() -> redis.exists(FlashKeys.sale(saleId)));
    }

    /**
     * Nạp hoặc dựng lại runtime của một chương trình từ số liệu Oracle. Mỗi sản phẩm được nạp nguyên tử
     * (load-item.lua); reservation đang chờ bị bỏ, suất đó được mở bán lại. Trả số reservation đã bỏ.
     */
    public long loadSale(SaleRuntime sale, List<ItemRuntime> items) {
        long dropped = 0;
        for (ItemRuntime item : items) {
            List<String> args = new ArrayList<>(List.of(String.valueOf(sale.saleId()),
                    String.valueOf(item.productId()), String.valueOf(item.allocated()),
                    String.valueOf(item.remaining()), item.status()));
            item.purchasedByUser().forEach((userId, qty) -> {
                args.add(String.valueOf(userId));
                args.add(String.valueOf(qty));
            });
            dropped += asLong(scripts.run(Script.LOAD_ITEM,
                    List.of(FlashKeys.item(item.itemId()), FlashKeys.userQty(item.itemId()),
                            FlashKeys.pending(item.itemId()), FlashKeys.EXPIRY),
                    args));
        }
        // Ghi chương trình sau cùng: trong lúc nạp, giữ suất thấy chương trình chưa có và bị từ chối.
        // itemIds để API suất còn lại chỉ cần đọc Redis.
        String itemIds = String.join(",", items.stream().map(i -> String.valueOf(i.itemId())).toList());
        call(() -> redis.hset(FlashKeys.sale(sale.saleId()), Map.of(
                "status", sale.status(),
                "itemIds", itemIds,
                "startAt", String.valueOf(sale.startAtMs()),
                "endAt", String.valueOf(sale.endAtMs()),
                "maxPerUser", String.valueOf(sale.maxPerUser()),
                "reservationTtl", String.valueOf(sale.reservationTtlSeconds()))));
        return dropped;
    }

    /** Đổi trạng thái chương trình trên Redis (ví dụ SCHEDULED -> ACTIVE). */
    public void setSaleStatus(long saleId, String status) {
        call(() -> redis.hset(FlashKeys.sale(saleId), "status", status));
    }

    /** Xóa runtime của chương trình (kết thúc, hủy, hủy lịch). Reservation tự hết TTL hoặc do job dọn. */
    public void unloadSale(long saleId, Collection<Long> itemIds) {
        List<String> keys = new ArrayList<>();
        keys.add(FlashKeys.sale(saleId));
        for (Long itemId : itemIds) {
            keys.add(FlashKeys.item(itemId));
            keys.add(FlashKeys.userQty(itemId));
            keys.add(FlashKeys.pending(itemId));
        }
        call(() -> redis.unlink(keys.toArray(String[]::new)));
    }

    private String hget(String key, String field) {
        return call(() -> redis.hget(key, field));
    }

    private static <T> T call(java.util.function.Supplier<T> command) {
        try {
            return command.get();
        } catch (RuntimeException e) {
            throw RedisFailures.translate(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object reply) {
        return (List<Object>) reply;
    }

    private static long asLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        return Long.parseLong(text(value));
    }

    private static String text(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                : String.valueOf(value);
    }
}
