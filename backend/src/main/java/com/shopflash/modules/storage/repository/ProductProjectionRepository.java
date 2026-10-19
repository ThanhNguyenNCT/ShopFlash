package com.shopflash.modules.storage.repository;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.storage.model.CatalogProduct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.Response;
import redis.clients.jedis.commands.ProtocolCommand;

/** Ghi và đo RedisJSON projection product:{id}; không nhận dữ liệu ghi trực tiếp từ client. */
@Repository
public class ProductProjectionRepository {

    private static final int SCAN_COUNT = 1_000;
    private static final String CONSTRAINT_DEMO_KEY_PREFIX = "storage-lab:constraint-demo:";
    private static final String CONSTRAINT_DEMO_DOCUMENT = """
            {"id":-1,"sku":"LAB-INVALID","name":"Storage Lab invalid document","categoryId":999999999,"categoryName":null,"price":-1,"status":"ACTIVE","inStock":true,"thumbnailUrl":null}
            """.trim();
    private final RedisClient redis;

    public ProductProjectionRepository(RedisClient redis) {
        this.redis = redis;
    }

    public void upsert(CatalogProduct product) {
        call("JSON.SET", key(product.product().productId()), "$", document(product));
    }

    public void upsertAll(List<CatalogProduct> products) {
        if (products.isEmpty()) return;
        try (AbstractPipeline pipeline = redis.pipelined()) {
            for (CatalogProduct product : products) {
                pipeline.sendCommand(command("JSON.SET"), key(product.product().productId()), "$", document(product));
            }
            pipeline.sync();
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    public long documentCount() {
        return scanKeys().size();
    }

    public long documentBytes() {
        List<String> keys = scanKeys();
        if (keys.isEmpty()) return 0;
        try (AbstractPipeline pipeline = redis.pipelined()) {
            List<Response<Object>> replies = new ArrayList<>(keys.size());
            for (String key : keys) replies.add(pipeline.sendCommand(command("MEMORY"), "USAGE", key));
            pipeline.sync();
            long total = 0;
            for (Response<Object> reply : replies) total += longValue(reply.get());
            return total;
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    /**
     * Chứng minh RedisJSON không tự áp FK/CHECK như Oracle. Key tạm không có prefix {@code product:},
     * nên không được Redis search index nhận; luôn được UNLINK trước khi trả response.
     */
    public ConstraintDemo runConstraintDemo() {
        String key = CONSTRAINT_DEMO_KEY_PREFIX + java.util.UUID.randomUUID();
        boolean mustCleanUp = false;
        try {
            call("JSON.SET", key, "$", CONSTRAINT_DEMO_DOCUMENT);
            mustCleanUp = true;
            String stored = text(call("JSON.GET", key, "$"));
            long removed = longValue(call("UNLINK", key));
            mustCleanUp = false;
            if (removed != 1) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REDIS_CLEANUP_FAILED",
                        "Không thể dọn dữ liệu kiểm thử Redis");
            }
            return new ConstraintDemo(key, "JSON.SET " + key + " $ '" + CONSTRAINT_DEMO_DOCUMENT + "'", stored);
        } catch (RuntimeException error) {
            if (mustCleanUp) {
                try {
                    call("UNLINK", key);
                } catch (RuntimeException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            throw error;
        }
    }

    /** Xóa document cũ chỉ khi nó không còn Product Oracle nào tương ứng. */
    public void removeNotIn(List<String> activeKeys) {
        var expected = new java.util.HashSet<>(activeKeys);
        List<String> stale = scanKeys().stream().filter(key -> !expected.contains(key)).toList();
        if (stale.isEmpty()) return;
        try (AbstractPipeline pipeline = redis.pipelined()) {
            for (String key : stale) pipeline.sendCommand(command("UNLINK"), key);
            pipeline.sync();
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    public static String key(long productId) {
        return "product:" + productId;
    }

    /** Lệnh và document của một lần demo đã được dọn key trước khi đối tượng này được trả về. */
    public record ConstraintDemo(String key, String redisCommand, String storedDocument) {
    }

    private List<String> scanKeys() {
        try {
            List<String> keys = new ArrayList<>();
            String cursor = "0";
            do {
                List<?> reply = list(call("SCAN", cursor, "MATCH", "product:*", "COUNT", String.valueOf(SCAN_COUNT)));
                cursor = text(reply.get(0));
                for (Object key : list(reply.get(1))) keys.add(text(key));
            } while (!"0".equals(cursor));
            return keys;
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    private Object call(String command, String... args) {
        try {
            return redis.sendCommand(command(command), args);
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    private String document(CatalogProduct value) {
        var product = value.product();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", product.productId());
        fields.put("sku", product.sku());
        fields.put("name", product.name());
        fields.put("categoryId", product.categoryId());
        fields.put("categoryName", value.categoryName());
        fields.put("price", product.basePrice());
        fields.put("status", product.status().name());
        fields.put("inStock", value.inStock());
        fields.put("thumbnailUrl", product.thumbnailUrl());
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (var entry : fields.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('"').append(entry.getKey()).append("\":");
            appendJsonValue(out, entry.getValue());
        }
        return out.append('}').toString();
    }

    private static ProtocolCommand command(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        return () -> bytes;
    }

    @SuppressWarnings("unchecked")
    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private static String text(Object value) {
        return value instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(value);
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return 0;
        return Long.parseLong(text(value));
    }

    private static void appendJsonValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else {
            out.append('"').append(escape(value.toString())).append('"');
        }
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static ApiException unavailable(RuntimeException error) {
        if (error instanceof ApiException api) return api;
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE", "Không kết nối được Redis", null, error);
    }
}
