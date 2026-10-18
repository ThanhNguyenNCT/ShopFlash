package com.shopflash.modules.query.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    /** POST đổi trạng thái gửi Content-Type khác application/json (chặn form HTML / CSRF đơn giản). */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** Đã có một lần rebuild read model Redis đang chạy. */
    REBUILD_IN_PROGRESS(HttpStatus.CONFLICT),
    REDIS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    REDIS_NOT_BUILT(HttpStatus.SERVICE_UNAVAILABLE),
    ORACLE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
