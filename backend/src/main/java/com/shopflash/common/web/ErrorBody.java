package com.shopflash.common.web;

import java.util.Map;

/**
 * Định dạng lỗi chung của mọi API: { "error": { "code", "message", "details" } }.
 * Frontend (shared/api/client.js) đọc đúng dạng này để hiện mã lỗi và thông báo.
 */
public record ErrorBody(Error error) {

    public record Error(String code, String message, Map<String, Object> details) {
    }

    public static ErrorBody of(String code, String message, Map<String, Object> details) {
        return new ErrorBody(new Error(code, message, details));
    }
}
