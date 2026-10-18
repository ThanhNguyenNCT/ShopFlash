package com.shopflash.modules.query.exception;

import java.util.Map;

/** Lỗi trả về client dạng { "error": { code, message, details } }; QueryExceptionHandler đổi thành response. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details;
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message, Map.of("field", field), null);
    }

    public ErrorCode code() { return code; }

    public Map<String, Object> details() { return details; }
}
