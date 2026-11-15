package com.shopflash.modules.concurrency.lab;

import java.sql.SQLException;

import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import com.shopflash.common.web.ApiException;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

/** Đặt mã ngắn cho lỗi của một lượt mua để đếm theo mã. Lỗi Oracle lấy mã ORA-xxxxx từ SQLException. */
public final class LabErrorCodes {

    private LabErrorCodes() {
    }

    public static String of(Throwable error) {
        int vendorCode = oracleCode(error);
        if (vendorCode > 0) {
            return String.format("ORA-%05d", vendorCode);
        }
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ApiException api) {
                return api.code();
            }
            if (t instanceof CannotGetJdbcConnectionException || t instanceof TransientDataAccessResourceException) {
                return "DB_CONNECTION_TIMEOUT";
            }
            if (t instanceof JedisConnectionException) {
                return "REDIS_UNAVAILABLE";
            }
            if (t instanceof JedisException) {
                return "REDIS_ERROR";
            }
        }
        return error.getClass().getSimpleName();
    }

    /** Mã lỗi Oracle (ví dụ 8177 cho ORA-08177) tìm trong chuỗi cause, 0 nếu không có. */
    public static int oracleCode(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() > 0) {
                return sql.getErrorCode();
            }
        }
        return 0;
    }
}
