package com.shopflash.modules.query.exception;

import com.shopflash.modules.query.dto.response.ErrorResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import tools.jackson.core.JacksonException;

/**
 * Đổi mọi lỗi của module query thành { "error": { code, message, details } }.
 * Content-Type luôn đặt sẵn là JSON để lỗi vẫn trả được khi client gửi Accept: text/csv.
 * Lỗi không lường trước chỉ ghi log, không trả stack trace.
 */
@RestControllerAdvice(basePackages = "com.shopflash.modules.query")
// Chạy trước GlobalExceptionHandler (common/web) cho controller của module này.
@Order(Ordered.HIGHEST_PRECEDENCE)
public class QueryExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(QueryExceptionHandler.class);

    /** Thông báo tiếng Việt cho lỗi bean validation / body sai, theo tên trường. */
    private static final Map<String, String> FIELD_MESSAGES = Map.of(
            "limit", "limit phải từ 1 đến 100",
            "warmup", "warmup phải từ 0 đến 5",
            "runs", "runs phải từ 1 đến 30",
            "from", "from phải là ngày hợp lệ dạng YYYY-MM-DD",
            "to", "to phải là ngày hợp lệ dạng YYYY-MM-DD",
            "action", "action phải là GATHER hoặc DELETE",
            "variants", "variants phải là danh sách tối đa 12 phần tử { source, variant }");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        if (e.code().status().is5xxServerError()) {
            log.warn("Query API lỗi {}: {}", e.code(), e.getMessage(), e.getCause());
        }
        return build(e.code().status(), e.code(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String field = fe != null ? fe.getField() : "body";
        return validation(field);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        String field = e.getParameterValidationResults().stream()
                .map(r -> r.getMethodParameter().getParameterName())
                .filter(n -> n != null)
                .findFirst().orElse("request");
        return validation(field);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return validation(e.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        String field = fieldFromJackson(e);
        if (field == null) {
            return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                    "Nội dung request không phải JSON hợp lệ", Map.of("field", "body"));
        }
        return validation(field);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleOracle(DataAccessException e) {
        log.error("Lỗi Oracle trong module query", e);
        ApiException api = ApiException.oracle("Lỗi khi truy vấn Oracle", e);
        return build(ErrorCode.ORACLE_ERROR.status(), ErrorCode.ORACLE_ERROR, api.getMessage(), api.details());
    }

    /** Sai Content-Type ở các POST chỉ nhận JSON (consumes = application/json). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return handleApi(ApiException.unsupportedMediaType());
    }

    @ExceptionHandler(JedisConnectionException.class)
    public ResponseEntity<ErrorResponse> handleRedisDown(JedisConnectionException e) {
        log.warn("Không kết nối được Redis: {}", e.getMessage());
        return build(ErrorCode.REDIS_UNAVAILABLE.status(), ErrorCode.REDIS_UNAVAILABLE, "Không kết nối được Redis", null);
    }

    /**
     * Lỗi không lường trước: 500 INTERNAL_ERROR. Riêng lỗi Spring MVC đã mang status 4xx (405, 406, 415, ...)
     * thì giữ status đó, code VALIDATION_ERROR.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return build(er.getStatusCode(), ErrorCode.VALIDATION_ERROR, "Request không hợp lệ", null);
        }
        log.error("Lỗi không mong đợi trong module query", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Lỗi hệ thống, vui lòng thử lại", null);
    }

    private static ResponseEntity<ErrorResponse> validation(String field) {
        String message = FIELD_MESSAGES.getOrDefault(rootField(field), field + " không hợp lệ");
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, Map.of("field", field));
    }

    /** "variants[0].source" -> "variants" để tra thông báo. */
    private static String rootField(String field) {
        int cut = field.length();
        for (char c : new char[] {'[', '.'}) {
            int i = field.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        return field.substring(0, cut);
    }

    private static String fieldFromJackson(HttpMessageNotReadableException e) {
        Throwable t = e.getCause();
        while (t != null && !(t instanceof JacksonException)) {
            t = t.getCause();
        }
        if (!(t instanceof JacksonException je)) {
            return null;
        }
        List<JacksonException.Reference> path = je.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : path) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatusCode status, ErrorCode code, String message,
            Map<String, Object> details) {
        Map<String, Object> d = details == null ? null : new LinkedHashMap<>(details);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(new ErrorResponse.Body(code.name(), message, d)));
    }
}
