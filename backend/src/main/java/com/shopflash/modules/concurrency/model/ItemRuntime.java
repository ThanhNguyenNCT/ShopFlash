package com.shopflash.modules.concurrency.model;

import java.util.Map;

/**
 * Một sản phẩm trong chương trình, đọc từ Oracle để nạp lên Redis.
 * purchasedByUser: userId -> số lượng đã mua trong các đơn chưa hủy (dựng lại user_qty).
 */
public record ItemRuntime(long itemId, long productId, int allocated, int sold, String status,
                          Map<Long, Integer> purchasedByUser) {

    public ItemRuntime {
        purchasedByUser = Map.copyOf(purchasedByUser);
    }

    public int remaining() {
        return allocated - sold;
    }
}
