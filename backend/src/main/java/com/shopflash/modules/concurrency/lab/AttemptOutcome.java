package com.shopflash.modules.concurrency.lab;

/**
 * Kết quả một lượt mua. retries là số lần thử lại (0 nếu xong ngay lần đầu).
 * errorCode chỉ có khi result = ERROR, ví dụ ORA-00054, ORA-08177, RETRY_EXHAUSTED.
 */
public record AttemptOutcome(Result result, int retries, String errorCode) {

    public enum Result {
        SUCCESS,
        SOLD_OUT,
        ERROR
    }

    public static AttemptOutcome success(int retries) {
        return new AttemptOutcome(Result.SUCCESS, retries, null);
    }

    public static AttemptOutcome soldOut(int retries) {
        return new AttemptOutcome(Result.SOLD_OUT, retries, null);
    }

    public static AttemptOutcome error(String code, int retries) {
        return new AttemptOutcome(Result.ERROR, retries, code);
    }
}
