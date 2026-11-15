package com.shopflash.modules.concurrency.repository.redis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import redis.clients.jedis.RedisClient;
import redis.clients.jedis.exceptions.JedisNoScriptException;

/**
 * Lua script của module, nằm ở src/main/resources/concurrency/redis/. SHA1 tính sẵn khi khởi động (giống SHA mà
 * SCRIPT LOAD trả về) nên backend không cần Redis lúc khởi động. Gọi bằng EVALSHA; Redis chưa có script
 * (mới khởi động lại, SCRIPT FLUSH) thì báo NOSCRIPT, khi đó SCRIPT LOAD rồi gọi lại đúng một lần.
 */
@Component
public class LuaScripts {

    public enum Script {
        RESERVE("reserve.lua"),
        CLAIM("claim.lua"),
        CONFIRM("confirm.lua"),
        RELEASE("release.lua"),
        LOAD_ITEM("load-item.lua");

        private final String file;

        Script(String file) {
            this.file = file;
        }
    }

    private record Loaded(String source, String sha) {
    }

    private final RedisClient redis;
    private final Map<Script, Loaded> scripts = new EnumMap<>(Script.class);

    public LuaScripts(RedisClient redis) {
        this.redis = redis;
        for (Script script : Script.values()) {
            String source = read("concurrency/redis/" + script.file);
            scripts.put(script, new Loaded(source, sha1(source)));
        }
    }

    /** Chạy script, trả reply thô của Jedis (chuỗi là String, số nguyên là Long, mảng là List). */
    public Object run(Script script, List<String> keys, List<String> args) {
        Loaded loaded = scripts.get(script);
        try {
            try {
                return redis.evalsha(loaded.sha(), keys, args);
            } catch (JedisNoScriptException e) {
                redis.scriptLoad(loaded.source());
                return redis.evalsha(loaded.sha(), keys, args);
            }
        } catch (RuntimeException e) {
            throw RedisFailures.translate(e);
        }
    }

    public String sha(Script script) {
        return scripts.get(script).sha();
    }

    static String sha1(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(source.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không có SHA-1", e);
        }
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được Lua script " + path, e);
        }
    }
}
