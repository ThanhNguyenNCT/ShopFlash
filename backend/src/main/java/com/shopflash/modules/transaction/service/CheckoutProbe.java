package com.shopflash.modules.transaction.service;

/** Điểm đồng bộ và gây lỗi chỉ được đăng ký bằng bean trong bài test; production không có bean này. */
public interface CheckoutProbe {
    default void beforeCartLock() {}
    default void afterCartLock() {}
    default void afterProductLock() {}
    default void beforeOrderInsert() {}
    default void afterOrderWrite() {}
    default void afterOrderItemsWrite() {}
    default void afterInventoryWrite() {}
    default void beforeCartClear() {}
}
