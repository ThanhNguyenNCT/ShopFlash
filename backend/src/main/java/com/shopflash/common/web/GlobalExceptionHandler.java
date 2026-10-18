package com.shopflash.common.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Đổi lỗi của mọi module sang {@link ErrorBody}. Chạy sau cùng (LOWEST_PRECEDENCE): module nào có handler riêng
 * (ví dụ QueryExceptionHandler) thì handler đó được ưu tiên cho controller của module.
 * Lỗi không lường trước chỉ ghi log, client nhận thông báo chung, không có stack trace hay lỗi driver.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> handleApi(ApiException e) {
        if (e.status().is5xxServerError()) {
            log.error("API lỗi {}: {}", e.code(), e.getMessage(), e);
        }
        return body(e.status(), e.code(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleInvalidBody(MethodArgumentNotValidException e) {
        FieldError first = e.getBindingResult().getFieldError();
        Map<String, Object> details = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> details.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String message = first == null ? "Dữ liệu gửi lên không hợp lệ" : first.getField() + ": " + first.getDefaultMessage();
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> handleUnreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Body không phải JSON hợp lệ", Map.of("field", "body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số " + e.getName() + " sai kiểu",
                Map.of("field", e.getName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorBody> handleMissingParam(MissingServletRequestParameterException e) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Thiếu tham số " + e.getParameterName(),
                Map.of("field", e.getParameterName()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không có API này", Map.of("path", "/" + e.getResourcePath()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMethod(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "API không hỗ trợ phương thức " + e.getMethod(), null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorBody> handleDataAccess(DataAccessException e) {
        log.error("Lỗi truy cập Oracle", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "ORACLE_ERROR", "Lỗi khi truy cập Oracle, xem log backend", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleUnexpected(Exception e) {
        log.error("Lỗi không mong đợi", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống, vui lòng thử lại", null);
    }

    private static ResponseEntity<ErrorBody> body(HttpStatus status, String code, String message, Map<String, Object> details) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorBody.of(code, message, details));
    }
}
