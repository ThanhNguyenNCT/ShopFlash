package com.shopflash.modules.concurrency.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Detail;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.InvariantViolation;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Rebuild;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.Runtime;
import com.shopflash.modules.concurrency.dto.response.AdminFlashSaleResponses.RuntimeItem;
import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.repository.oracle.FlashAdminRepository;
import com.shopflash.modules.concurrency.repository.oracle.FlashAdminRepository.ItemRow;
import com.shopflash.modules.concurrency.repository.oracle.FlashAdminRepository.SaleRow;
import com.shopflash.modules.concurrency.repository.oracle.FlashLifecycleRepository;
import com.shopflash.modules.concurrency.repository.oracle.FlashLifecycleRepository.LockedItem;
import com.shopflash.modules.concurrency.repository.oracle.FlashLifecycleRepository.SaleState;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;

/**
 * Chuyển trạng thái chương trình (AGENTS.md, "Vòng đời chương trình"). Mỗi lần chuyển là một transaction Oracle theo
 * thứ tự khóa FLASH_SALES → FLASH_SALE_ITEMS → PRODUCT_INVENTORY (product_id tăng dần); Redis đổi sau commit. Redis
 * lỗi sau commit thì chỉ ghi log: job nạp lại runtime còn thiếu, và Oracle vẫn chặn đơn của chương trình không còn bán.
 */
@Service
public class FlashLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(FlashLifecycleService.class);

    private final FlashLifecycleRepository lifecycle;
    private final FlashAdminRepository admin;
    private final FlashAdminService adminService;
    private final FlashRuntimeRepository runtime;
    private final FlashRuntimeService runtimeService;
    private final TransactionTemplate transaction;
    private final int endGraceSeconds;

    public FlashLifecycleService(FlashLifecycleRepository lifecycle, FlashAdminRepository admin,
            FlashAdminService adminService, FlashRuntimeRepository runtime, FlashRuntimeService runtimeService,
            PlatformTransactionManager transactions,
            @Value("${shopflash.flash.end-grace-seconds:120}") int endGraceSeconds) {
        this.lifecycle = lifecycle;
        this.admin = admin;
        this.adminService = adminService;
        this.runtime = runtime;
        this.runtimeService = runtimeService;
        this.transaction = new TransactionTemplate(transactions);
        this.endGraceSeconds = endGraceSeconds;
    }

    /** DRAFT → SCHEDULED: giữ hàng trong kho cho mọi sản phẩm (đủ cả hoặc không gì cả), rồi nạp runtime Redis. */
    public Detail schedule(long saleId) {
        transaction.executeWithoutResult(s -> {
            if (!lifecycle.schedule(saleId)) {
                throw whyNot(saleId, "DRAFT", "Giờ bắt đầu đã qua, sửa giờ trước khi lên lịch");
            }
            List<LockedItem> items = lifecycle.lockActiveItems(saleId);
            if (items.isEmpty()) {
                throw FlashAdminService.invalidTransition("Chương trình chưa có sản phẩm");
            }
            for (LockedItem item : byProduct(items)) {
                if (!"ACTIVE".equals(item.productStatus())) {
                    throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Sản phẩm đang ngừng bán",
                            Map.of("productId", item.productId()), null);
                }
                if (!lifecycle.reserveStock(item.productId(), item.allocated())) {
                    throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                            "Không đủ hàng trong kho để giữ cho flash sale", Map.of("productId", item.productId()),
                            null);
                }
            }
            // Kiểm tra trùng giờ lại ở đây, sau khi đã khóa PRODUCT_INVENTORY của mọi sản phẩm: lúc soạn (DRAFT) chỉ
            // so với chương trình đã lên lịch, nên hai DRAFT cùng sản phẩm đều qua được. Hai lệnh lên lịch chạy cùng
            // lúc thì bên sau chờ khóa tồn kho, tới lượt đã thấy bên trước SCHEDULED.
            SaleState state = lifecycle.state(saleId).orElseThrow(FlashErrors::saleNotFound);
            List<Long> busy = admin.productsInOverlappingSales(saleId, state.startAt(), state.endAt(),
                    items.stream().map(LockedItem::productId).toList());
            if (!busy.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE",
                        "Sản phẩm đã nằm trong chương trình khác trùng giờ", Map.of("productId", busy.getFirst()),
                        null);
            }
        });
        loadQuietly(saleId);
        return adminService.detail(saleId);
    }

    /** SCHEDULED → DRAFT, chỉ khi còn hơn 60 s trước giờ mở bán: trả hàng về kho, xóa runtime. */
    public Detail unschedule(long saleId) {
        transaction.executeWithoutResult(s -> {
            if (!lifecycle.unschedule(saleId)) {
                throw whyNot(saleId, "SCHEDULED", "Còn dưới 60 giây tới giờ mở bán, không hủy lịch được");
            }
            returnUnsold(saleId);
        });
        unloadQuietly(saleId);
        return adminService.detail(saleId);
    }

    /** DRAFT → CANCELLED; SCHEDULED → CANCELLED như hủy lịch (trả hàng về kho). ACTIVE thì dùng stop. */
    public Detail cancel(long saleId) {
        boolean wasScheduled = Boolean.TRUE.equals(transaction.execute(s -> {
            if (lifecycle.cancelDraft(saleId)) {
                return false;
            }
            if (!lifecycle.cancelScheduled(saleId)) {
                SaleState state = lifecycle.state(saleId).orElseThrow(FlashErrors::saleNotFound);
                if ("SCHEDULED".equals(state.status())) {
                    throw FlashAdminService.invalidTransition("Còn dưới 60 giây tới giờ mở bán, không hủy được");
                }
                throw FlashAdminService.invalidTransition("Chương trình đang ở trạng thái " + state.status()
                        + ("ACTIVE".equals(state.status()) ? "; dùng dừng sớm" : ""));
            }
            returnUnsold(saleId);
            return true;
        }));
        if (wasScheduled) {
            unloadQuietly(saleId);
        }
        return adminService.detail(saleId);
    }

    /**
     * Dừng sớm chương trình ACTIVE: ghi endAt vào Redis trước để reserve.lua chặn giữ suất ngay, rồi end_at = cùng
     * giờ đó trong Oracle. Job chuyển sang ENDED sau end_at + thời gian giữ suất + mốc an toàn.
     */
    public Detail stop(long saleId) {
        SaleState state = lifecycle.state(saleId).orElseThrow(FlashErrors::saleNotFound);
        if (!"ACTIVE".equals(state.status())) {
            throw FlashAdminService.invalidTransition("Chỉ dừng sớm được chương trình đang chạy");
        }
        long nowMs = runtime.serverTimeMs();
        runtime.setSaleEndAt(saleId, nowMs);
        boolean stopped;
        try {
            stopped = lifecycle.stop(saleId, FlashTime.ofEpochMs(nowMs));
        } catch (RuntimeException e) {
            // Oracle lỗi: chương trình chưa dừng, trả endAt trên Redis về giờ cũ.
            restoreEndAt(saleId, state);
            throw e;
        }
        if (!stopped) {
            // Trạng thái vừa đổi: đặt lại endAt trên Redis theo Oracle.
            restoreEndAt(saleId, lifecycle.state(saleId).orElse(state));
            throw whyNot(saleId, "ACTIVE", "Chương trình đã hết giờ hoặc đã dừng");
        }
        return adminService.detail(saleId);
    }

    private void restoreEndAt(long saleId, SaleState state) {
        try {
            runtime.setSaleEndAt(saleId, state.endAt().toInstant().toEpochMilli());
        } catch (RuntimeException e) {
            log.warn("Chưa đặt lại được endAt của chương trình {} trên Redis; dựng lại runtime để sửa: {}", saleId,
                    e.getMessage());
        }
    }

    /** Job: SCHEDULED → ACTIVE khi tới giờ. Giờ mở bán thật do reserve.lua quyết định; đây chỉ đổi trạng thái. */
    public List<Long> activateDue() {
        List<Long> activated = transaction.execute(s -> {
            List<Long> done = new ArrayList<>();
            for (long saleId : lifecycle.lockDueForActivation()) {
                if (lifecycle.activate(saleId)) {
                    done.add(saleId);
                }
            }
            return done;
        });
        for (long saleId : activated) {
            try {
                if (!runtime.setSaleStatus(saleId, "ACTIVE")) {
                    runtimeService.load(saleId);
                }
            } catch (RuntimeException e) {
                log.warn("Chương trình {} đã ACTIVE trong Oracle nhưng chưa cập nhật được Redis: {}", saleId,
                        e.getMessage());
            }
        }
        return activated;
    }

    /** Job: ACTIVE → ENDED sau end_at + thời gian giữ suất + mốc an toàn; trả phần chưa bán về kho, xóa runtime. */
    public List<Long> endDue() {
        List<Long> ended = new ArrayList<>();
        // Mỗi chương trình một transaction: một chương trình lỗi (bất biến tồn kho lệch) không chặn các chương trình khác.
        for (long saleId : lifecycle.dueForEnding(endGraceSeconds)) {
            try {
                boolean done = Boolean.TRUE.equals(transaction.execute(s -> {
                    if (!lifecycle.lockForEnding(saleId, endGraceSeconds)) {
                        return false;   // instance khác đang xử lý, hoặc trạng thái vừa đổi
                    }
                    returnUnsold(saleId);
                    return lifecycle.end(saleId);
                }));
                if (done) {
                    ended.add(saleId);
                }
            } catch (RuntimeException e) {
                log.error("Chưa kết thúc được chương trình {}, lần sau thử lại: {}", saleId, e.getMessage());
            }
        }
        ended.forEach(this::unloadQuietly);
        return ended;
    }

    /** Đối chiếu từng sản phẩm giữa Oracle và Redis, kèm các dòng lệch của view bất biến. */
    public Runtime runtimeView(long saleId) {
        SaleRow sale = admin.sale(saleId).orElseThrow(FlashErrors::saleNotFound);
        List<ItemRow> items = admin.items(saleId);
        List<Long> itemIds = items.stream().map(ItemRow::itemId).toList();
        Map<Long, Long> remaining = runtime.remaining(itemIds);
        Map<Long, Long> pending = runtime.pendingCounts(itemIds);
        List<RuntimeItem> rows = new ArrayList<>();
        for (ItemRow item : items) {
            Long left = remaining.get(item.itemId());
            Long held = left == null ? null : item.allocated() - item.sold() - left;
            rows.add(new RuntimeItem(item.itemId(), item.productId(), item.productName(), item.allocated(),
                    item.sold(), left, held, pending.getOrDefault(item.itemId(), 0L), held != null && held >= 0));
        }
        List<InvariantViolation> violations = lifecycle.invariantViolations(saleId).stream()
                .map(r -> new InvariantViolation(((Number) r.get("PRODUCT_ID")).longValue(),
                        r.get("RESERVED_FLASH_QUANTITY") == null ? null
                                : ((Number) r.get("RESERVED_FLASH_QUANTITY")).longValue(),
                        ((Number) r.get("EXPECTED_RESERVED")).longValue()))
                .toList();
        return new Runtime(saleId, sale.status(), runtime.saleLoaded(saleId),
                FlashTime.ofEpochMs(runtime.serverTimeMs()), rows, violations);
    }

    /** Dựng lại runtime từ Oracle (reservation đang chờ bị bỏ, suất đó mở bán lại). */
    public Rebuild rebuild(long saleId) {
        FlashRuntimeService.LoadResult result = runtimeService.load(saleId);
        return new Rebuild(saleId, result.items(), result.droppedReservations());
    }

    /** Trả allocated - sold của từng sản phẩm về kho thường. Gọi trong transaction, sau khi đã khóa flash_sales. */
    private void returnUnsold(long saleId) {
        for (LockedItem item : byProduct(lifecycle.lockActiveItems(saleId))) {
            if (item.unsold() > 0 && !lifecycle.releaseStock(item.productId(), item.unsold())) {
                log.error("reserved_flash của sản phẩm {} nhỏ hơn {} khi trả hàng của chương trình {}: bất biến đã lệch",
                        item.productId(), item.unsold(), saleId);
                throw new IllegalStateException("Bất biến tồn kho flash sale đã lệch ở sản phẩm " + item.productId());
            }
        }
    }

    /** Sắp theo product_id tăng dần: mọi module khóa PRODUCT_INVENTORY theo cùng thứ tự để tránh deadlock. */
    private static List<LockedItem> byProduct(List<LockedItem> items) {
        return items.stream().sorted(Comparator.comparingLong(LockedItem::productId)).toList();
    }

    private ApiException whyNot(long saleId, String expectedStatus, String timeMessage) {
        SaleState state = lifecycle.state(saleId).orElse(null);
        if (state == null) {
            return FlashErrors.saleNotFound();
        }
        if (!expectedStatus.equals(state.status())) {
            return FlashAdminService.invalidTransition("Chương trình đang ở trạng thái " + state.status());
        }
        return FlashAdminService.invalidTransition(timeMessage);
    }

    private void loadQuietly(long saleId) {
        try {
            runtimeService.load(saleId);
        } catch (RuntimeException e) {
            log.warn("Đã lên lịch chương trình {} nhưng chưa nạp được runtime, job sẽ nạp lại: {}", saleId,
                    e.getMessage());
        }
    }

    private void unloadQuietly(long saleId) {
        try {
            runtimeService.unload(saleId);
        } catch (RuntimeException e) {
            log.warn("Chưa xóa được runtime của chương trình {}: {}", saleId, e.getMessage());
        }
    }
}
