package com.shopflash.modules.concurrency.lab;

/**
 * Các chiến lược của Concurrency Lab: cùng kịch bản "N người tranh M suất", mỗi lượt mua 1 suất.
 * Bảng kỳ vọng nằm trong AGENTS.md của module (mục Concurrency Lab).
 */
public enum LabStrategy {
    ORACLE_NAIVE(Store.ORACLE),
    ORACLE_PESSIMISTIC(Store.ORACLE),
    ORACLE_NOWAIT(Store.ORACLE),
    ORACLE_OPTIMISTIC(Store.ORACLE),
    ORACLE_ATOMIC(Store.ORACLE),
    ORACLE_SERIALIZABLE(Store.ORACLE),
    REDIS_NAIVE(Store.REDIS),
    REDIS_WATCH(Store.REDIS),
    REDIS_DECR(Store.REDIS),
    REDIS_LUA(Store.REDIS);

    public enum Store {
        ORACLE,
        REDIS
    }

    /** Số lần thử lại tối đa của các chiến lược lạc quan (OPTIMISTIC, SERIALIZABLE, WATCH). */
    public static final int MAX_RETRIES = 5;

    private final Store store;

    LabStrategy(Store store) {
        this.store = store;
    }

    public Store store() {
        return store;
    }
}
