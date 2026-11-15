package com.shopflash.modules.concurrency.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.shopflash.modules.concurrency.dto.response.ActiveSalesResponse;
import com.shopflash.modules.concurrency.dto.response.StockResponse;
import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.repository.oracle.FlashCatalogQueries;
import com.shopflash.modules.concurrency.repository.oracle.FlashCatalogQueries.ItemRow;
import com.shopflash.modules.concurrency.repository.oracle.FlashCatalogQueries.SaleRow;
import com.shopflash.modules.concurrency.repository.redis.FlashRuntimeRepository;

/** Phía khách xem chương trình (ai cũng xem được). Giờ server lấy từ Redis, cùng đồng hồ Lua dùng để mở bán. */
@Service
public class FlashSaleService {

    /** Chương trình bắt đầu trong vòng bấy nhiêu ngày tới thì hiện ở mục "sắp diễn ra". */
    static final int UPCOMING_DAYS = 7;

    private final FlashCatalogQueries catalog;
    private final FlashRuntimeRepository runtime;

    public FlashSaleService(FlashCatalogQueries catalog, FlashRuntimeRepository runtime) {
        this.catalog = catalog;
        this.runtime = runtime;
    }

    public ActiveSalesResponse active() {
        List<SaleRow> sales = catalog.visibleSales(UPCOMING_DAYS);
        List<ItemRow> items = catalog.activeItems(sales.stream().map(SaleRow::saleId).toList());
        Map<Long, Long> remaining = runtime.remaining(items.stream().map(ItemRow::itemId).toList());
        long now = runtime.serverTimeMs();

        Map<Long, List<ActiveSalesResponse.Item>> bySale = new LinkedHashMap<>();
        for (ItemRow item : items) {
            long left = remaining.getOrDefault(item.itemId(), Math.max(0, item.allocated() - item.sold()));
            bySale.computeIfAbsent(item.saleId(), k -> new ArrayList<>()).add(new ActiveSalesResponse.Item(
                    item.itemId(), item.productId(), item.productName(), item.thumbnailUrl(), item.basePrice(),
                    item.salePrice(), item.allocated(), Math.max(0, left)));
        }
        List<ActiveSalesResponse.Sale> out = new ArrayList<>(sales.size());
        for (SaleRow sale : sales) {
            String phase = now < sale.startAt().toInstant().toEpochMilli() ? "UPCOMING" : "LIVE";
            out.add(new ActiveSalesResponse.Sale(sale.saleId(), sale.name(), sale.description(), phase,
                    FlashTime.vn(sale.startAt()), FlashTime.vn(sale.endAt()), sale.maxPerUser(),
                    sale.reservationTtlSeconds(), bySale.getOrDefault(sale.saleId(), List.of())));
        }
        return new ActiveSalesResponse(FlashTime.ofEpochMs(now), out);
    }

    /** Chỉ đọc Redis, để frontend hỏi lại mỗi 2 s. Chương trình chưa nạp runtime thì 404. */
    public StockResponse stock(long saleId) {
        List<Long> itemIds = runtime.saleItemIds(saleId).orElseThrow(FlashErrors::saleNotFound);
        Map<Long, Long> remaining = runtime.remaining(itemIds);
        List<StockResponse.ItemStock> items = new ArrayList<>(itemIds.size());
        for (Long itemId : itemIds) {
            Long left = remaining.get(itemId);
            if (left != null) {
                items.add(new StockResponse.ItemStock(itemId, Math.max(0, left)));
            }
        }
        return new StockResponse(FlashTime.ofEpochMs(runtime.serverTimeMs()), items);
    }
}
