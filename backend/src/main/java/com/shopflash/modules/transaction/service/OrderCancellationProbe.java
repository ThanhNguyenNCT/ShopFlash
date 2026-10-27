package com.shopflash.modules.transaction.service;

/** Điểm đồng bộ/gây lỗi trong integration test; production không đăng ký bean này. */
public interface OrderCancellationProbe {
    default void beforeOrderLock() {}
    default void afterOrderLock() {}
    default void afterInventoryRestore(long productId) {}
}
