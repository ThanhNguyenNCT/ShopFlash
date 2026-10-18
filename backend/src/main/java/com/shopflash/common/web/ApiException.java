package com.shopflash.common.web;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ trả về client theo {@link ErrorBody}. Module ném lỗi này từ service, không tự dựng ResponseEntity lỗi.
 *
 * <pre>{@code
 * throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm");
 * throw ApiException.badRequest("price", "Giá phải lớn hơn hoặc bằng 0");
 * }</pre>
 *
 * Mã lỗi (code) viết HOA_GẠCH_DƯỚI, thông báo tiếng Việt, không chứa nguyên văn lỗi của driver Oracle/Redis.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    /** 400 VALIDATION_ERROR gắn với một trường. */
    public static ApiException badRequest(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, Map.of("field", field), null);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /** 409 khi vi phạm quy tắc nghiệp vụ, ví dụ hủy đơn đã giao. */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus status() { return status; }

    public String code() { return code; }

    public Map<String, Object> details() { return details; }
}
