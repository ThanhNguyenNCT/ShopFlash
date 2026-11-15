package com.shopflash.modules.concurrency.service;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.model.ItemRuntime;
import com.shopflash.modules.concurrency.model.SaleRuntime;
import com.shopflash.modules.concurrency.repository.oracle.FlashRuntimeQueries;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;

/**
 * Oracle là nguồn gốc; runtime trên Redis dựng lại được: remaining = allocated - sold, user_qty đếm từ các
 * đơn chưa hủy. Dựng lại thì chỉ mất các reservation đang chờ (suất đó được mở bán lại), không bán vượt vì
 * Oracle vẫn chặn bằng điều kiện sold + q <= allocated khi đặt đơn.
 */
@Service
public class FlashRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(FlashRuntimeService.class);

    private final FlashRuntimeQueries queries;
    private final FlashRuntimeRepository runtime;

    public FlashRuntimeService(FlashRuntimeQueries queries, FlashRuntimeRepository runtime) {
        this.queries = queries;
        this.runtime = runtime;
    }

    public record LoadResult(long saleId, int items, long droppedReservations) {
    }

    private static final Set<String> RUNTIME_STATUSES = Set.of("SCHEDULED", "ACTIVE");

    /** Nạp (hoặc dựng lại) runtime của một chương trình SCHEDULED/ACTIVE từ Oracle. */
    public LoadResult load(long saleId) {
        SaleRuntime sale = queries.sale(saleId).orElseThrow(FlashErrors::saleNotFound);
        if (!RUNTIME_STATUSES.contains(sale.status())) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION",
                    "Chỉ dựng runtime cho chương trình đã lên lịch hoặc đang chạy");
        }
        List<ItemRuntime> items = queries.items(saleId);
        long dropped = runtime.loadSale(sale, items);
        // Admin có thể vừa hủy lịch trong lúc nạp (job nạp lại đọc trạng thái trước khi admin commit): kiểm tra
        // lại và gỡ runtime vừa nạp, để Redis không nhận giữ suất cho chương trình không còn bán.
        boolean stillRuntime = queries.sale(saleId).map(s -> RUNTIME_STATUSES.contains(s.status())).orElse(false);
        if (!stillRuntime) {
            unload(saleId);
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Chương trình vừa đổi trạng thái, không nạp runtime");
        }
        log.info("Nạp runtime flash sale {}: {} sản phẩm, bỏ {} reservation đang chờ", saleId, items.size(), dropped);
        return new LoadResult(saleId, items.size(), dropped);
    }

    /** Nạp mọi chương trình SCHEDULED/ACTIVE còn thiếu key runtime (sau khi Redis mất dữ liệu). */
    public int ensureLoaded() {
        int loaded = 0;
        for (long saleId : queries.runtimeSaleIds()) {
            if (!runtime.saleLoaded(saleId)) {
                try {
                    load(saleId);
                    loaded++;
                } catch (ApiException e) {
                    // Chương trình vừa đổi trạng thái; các chương trình khác vẫn nạp tiếp.
                    log.info("Bỏ qua nạp runtime flash sale {}: {}", saleId, e.getMessage());
                }
            }
        }
        return loaded;
    }

    /** Xóa runtime khi chương trình kết thúc, bị hủy hoặc hủy lịch. */
    public void unload(long saleId) {
        runtime.unloadSale(saleId, queries.itemIds(saleId));
    }
}
