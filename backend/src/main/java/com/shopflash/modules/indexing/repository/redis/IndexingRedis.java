package com.shopflash.modules.indexing.repository.redis;

import com.shopflash.common.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Gửi lệnh Redis thô của module indexing và đổi lỗi Jedis thành ApiException.
 * REDIS_UNAVAILABLE và REDIS_INDEX_MISSING là hai lỗi mà ProductSearchService chuyển sang Oracle Text.
 */
@Component
public class IndexingRedis {

    public static final String UNAVAILABLE = "REDIS_UNAVAILABLE";
    public static final String INDEX_MISSING = "REDIS_INDEX_MISSING";

    private final RedisClient client;

    public IndexingRedis(RedisClient client) {
        this.client = client;
    }

    /** Một lệnh, một round-trip. args[0] là tên lệnh. */
    public Object call(String... args) {
        try {
            return client.sendCommand(command(args[0]), tail(args));
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** Nhiều lệnh trong một pipeline (một round-trip); reply theo đúng thứ tự gửi. */
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

    static RuntimeException translate(RuntimeException e) {
        if (e instanceof ApiException api) {
            return api;
        }
        if (e instanceof JedisConnectionException || e.getCause() instanceof java.io.IOException) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "Không kết nối được Redis", null, e);
        }
        if (e instanceof JedisDataException && isMissingIndex(e.getMessage())) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, INDEX_MISSING,
                    "Chưa có index idx:products trên Redis, hãy chạy redis/init.lua", null, e);
        }
        if (e instanceof JedisException) {
            return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_ERROR", "Lỗi khi gửi lệnh Redis", null, e);
        }
        return e;
    }

    static boolean isMissingIndex(String message) {
        if (message == null) {
            return false;
        }
        String m = message.toLowerCase();
        return m.contains("no such index") || m.contains("unknown index");
    }

    static ProtocolCommand command(String name) {
        byte[] raw = name.getBytes(StandardCharsets.UTF_8);
        return () -> raw;
    }

    private static String[] tail(String[] args) {
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return rest;
    }

    /** Dạng redis-cli của một lệnh (để hiện trong meta.statement): tham số có khoảng trắng được bọc nháy. */
    public static String toCli(String... args) {
        StringBuilder sb = new StringBuilder();
        for (String a : args) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            boolean plain = !a.isEmpty() && a.chars().noneMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\'');
            sb.append(plain ? a : '"' + a.replace("\\", "\\\\").replace("\"", "\\\"") + '"');
        }
        return sb.toString();
    }

    /** Chuỗi của một phần tử reply RESP2 (byte[], Long, ...). */
    public static String str(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        if (o instanceof JedisDataException e) {
            throw translate(e);
        }
        return o.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        if (o instanceof JedisDataException e) {
            throw translate(e);
        }
        return o == null ? List.of() : (List<Object>) o;
    }
}
