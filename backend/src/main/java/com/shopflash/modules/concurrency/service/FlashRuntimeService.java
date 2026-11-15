package com.shopflash.modules.concurrency.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    /** Nạp (hoặc dựng lại) runtime của một chương trình từ Oracle. */
    public LoadResult load(long saleId) {
        SaleRuntime sale = queries.sale(saleId).orElseThrow(FlashErrors::saleNotFound);
        List<ItemRuntime> items = queries.items(saleId);
        long dropped = runtime.loadSale(sale, items);
        log.info("Nạp runtime flash sale {}: {} sản phẩm, bỏ {} reservation đang chờ", saleId, items.size(), dropped);
        return new LoadResult(saleId, items.size(), dropped);
    }

    /** Nạp mọi chương trình SCHEDULED/ACTIVE còn thiếu key runtime (sau khi Redis mất dữ liệu). */
    public int ensureLoaded() {
        int loaded = 0;
        for (long saleId : queries.runtimeSaleIds()) {
            if (!runtime.saleLoaded(saleId)) {
                load(saleId);
                loaded++;
            }
        }
        return loaded;
    }

    /** Xóa runtime khi chương trình kết thúc, bị hủy hoặc hủy lịch. */
    public void unload(long saleId) {
        runtime.unloadSale(saleId, queries.itemIds(saleId));
    }
}
