package com.shopflash.modules.query.repository.redis;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.shopflash.modules.query.exception.ApiException;
import com.shopflash.modules.query.exception.ErrorCode;

import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Gửi lệnh Redis thô (đúng như gõ ở redis-cli) và đổi lỗi Jedis thành ApiException.
 * Mọi lệnh trong module query đi qua đây để statement và số round-trip khớp với cái thực sự được gửi.
 */
@Component
public class RedisCommandRunner {

    private final RedisClient client;

    public RedisCommandRunner(RedisClient client) {
        this.client = client;
    }

    public RedisClient client() {
        return client;
    }

    /** Một lệnh, một round-trip. args[0] là tên lệnh, ví dụ "FT.AGGREGATE". */
    public Object call(String... args) {
        try {
            return client.sendCommand(command(args[0]), tail(args));
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** Gửi nhiều lệnh trong một pipeline (một round-trip), trả reply theo thứ tự. */
    public List<Object> pipeline(List<String[]> commands) {
        try (AbstractPipeline p = client.pipelined()) {
            List<Response<Object>> responses = new ArrayList<>(commands.size());
            for (String[] args : commands) {
                responses.add(p.sendCommand(command(args[0]), tail(args)));
            }
            p.sync();
            List<Object> out = new ArrayList<>(responses.size());
            for (Response<Object> r : responses) {
                out.add(r.get());
            }
            return out;
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** Chạy hàm trên pipeline; dùng cho lô JSON.SET/UNLINK lớn khi không cần đọc reply từng lệnh. */
    public <T> T withPipeline(Function<AbstractPipeline, T> body) {
        try (AbstractPipeline p = client.pipelined()) {
            T result = body.apply(p);
            p.sync();
            return result;
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    public static ProtocolCommand command(String name) {
        byte[] raw = name.getBytes(StandardCharsets.UTF_8);
        return () -> raw;
    }

    private static String[] tail(String[] args) {
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }

    public static RuntimeException translate(RuntimeException e) {
        if (e instanceof ApiException api) {
            return api;
        }
        if (e instanceof JedisConnectionException) {
            return new ApiException(ErrorCode.REDIS_UNAVAILABLE,
                    "Không kết nối được Redis: " + e.getMessage(), null, e);
        }
        if (e instanceof JedisDataException && isMissingIndex(e.getMessage())) {
            return new ApiException(ErrorCode.REDIS_NOT_BUILT,
                    "Chưa có index analytics trên Redis, hãy chạy redis/init.lua rồi rebuild: " + e.getMessage(),
                    null, e);
        }
        if (e instanceof JedisException) {
            Throwable cause = e.getCause();
            if (cause instanceof java.io.IOException) {
                return new ApiException(ErrorCode.REDIS_UNAVAILABLE,
                        "Không kết nối được Redis: " + e.getMessage(), null, e);
            }
            return new ApiException(ErrorCode.INTERNAL_ERROR, "Lỗi Redis: " + e.getMessage(), null, e);
        }
        return e;
    }

    static boolean isMissingIndex(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("no such index") || m.contains("unknown index name") || m.contains("unknown index");
    }

    /** Dạng redis-cli của một lệnh: tham số có khoảng trắng hoặc ký tự đặc biệt được bọc nháy. */
    public static String toCli(String... args) {
        StringBuilder sb = new StringBuilder();
        for (String a : args) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(quote(a));
        }
        return sb.toString();
    }

    private static String quote(String a) {
        if (!a.isEmpty() && a.chars().noneMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\'')) {
            return a;
        }
        if (a.indexOf('\'') < 0) {
            return "'" + a + "'";
        }
        return '"' + a.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
