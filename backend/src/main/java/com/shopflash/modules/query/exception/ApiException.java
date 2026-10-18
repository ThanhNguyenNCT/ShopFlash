package com.shopflash.modules.query.exception;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lỗi trả về client dạng { "error": { code, message, details } }; QueryExceptionHandler đổi thành response. */
public class ApiException extends RuntimeException {

    private static final Pattern ORA_CODE = Pattern.compile("ORA-\\d{5}");

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

    public static ApiException unsupportedMediaType() {
        return new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content-Type phải là application/json",
                Map.of("supported", List.of("application/json")), null);
    }

    /**
     * Lỗi Oracle: message cố định (không chép thông báo của driver ra API), chỉ đưa mã lỗi dạng ORA-NNNNN
     * vào details.oraCode nếu tìm thấy trong chuỗi cause. Chi tiết đầy đủ nằm trong log qua cause.
     */
    public static ApiException oracle(String message, Throwable cause) {
        String oraCode = oraCode(cause);
        return new ApiException(ErrorCode.ORACLE_ERROR, message,
                oraCode == null ? null : Map.of("oraCode", oraCode), cause);
    }

    /** Mã ORA-NNNNN đầu tiên trong thông báo của chuỗi cause, null nếu không có. */
    public static String oraCode(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            String m = c.getMessage();
            if (m != null) {
                Matcher matcher = ORA_CODE.matcher(m);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        }
        return null;
    }

    public ErrorCode code() { return code; }

    public Map<String, Object> details() { return details; }
}
