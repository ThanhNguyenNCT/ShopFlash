package com.shopflash.modules.query.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
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
