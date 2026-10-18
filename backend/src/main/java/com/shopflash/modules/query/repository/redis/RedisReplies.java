package com.shopflash.modules.query.repository.redis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import redis.clients.jedis.exceptions.JedisDataException;

/** Đọc reply RESP2 thô (byte[], Long, List) của FT.AGGREGATE, FT.CURSOR, FT.SEARCH, FT.INFO, FT.PROFILE. */
public final class RedisReplies {

    private RedisReplies() {
    }

    public static String str(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof byte[] b) {
            return new String(b, StandardCharsets.UTF_8);
        }
        if (o instanceof JedisDataException e) {
            throw e;
        }
        return o.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        if (o instanceof JedisDataException e) {
            throw e;
        }
        return o == null ? List.of() : (List<Object>) o;
    }

    public static long asLong(Object o) {
        if (o instanceof Long l) {
            return l;
        }
        String s = str(o);
        return s == null ? 0 : new BigDecimal(s).longValue();
    }

    /** Tiền: luôn scale 2, HALF_UP. Chuỗi số của RediSearch có thể dạng mũ (1.2e+09). */
    public static BigDecimal money(String s) {
        if (s == null || s.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
    }

    /** Các dòng của reply FT.AGGREGATE (phần tử 0 là tổng, sau đó mỗi dòng là mảng field/value phẳng). */
    public static List<Map<String, String>> aggregateRows(Object reply) {
        List<Object> all = list(reply);
        List<Map<String, String>> rows = new ArrayList<>(Math.max(0, all.size() - 1));
        for (int i = 1; i < all.size(); i++) {
            rows.add(pairs(all.get(i)));
        }
        return rows;
    }

    public static Map<String, String> pairs(Object flat) {
        List<Object> kv = list(flat);
        Map<String, String> m = new LinkedHashMap<>();
        for (int j = 0; j + 1 < kv.size(); j += 2) {
            m.put(str(kv.get(j)), str(kv.get(j + 1)));
        }
        return m;
    }

    /** Giá trị của một khóa trong reply dạng [k1, v1, k2, v2, ...] (FT.INFO). */
    public static Object field(Object flat, String key) {
        List<Object> kv = list(flat);
        for (int j = 0; j + 1 < kv.size(); j += 2) {
            if (key.equals(str(kv.get(j)))) {
                return kv.get(j + 1);
            }
        }
        return null;
    }

    /** Làm phẳng một reply lồng nhau (FT.PROFILE) thành các dòng dễ đọc, thụt lề theo cấp. */
    public static List<String> flatten(Object reply) {
        List<String> out = new ArrayList<>();
        flatten(reply, 0, out);
        return out;
    }

    /**
     * Reply của FT.PROFILE (RESP2) là mảng khóa/giá trị, trong đó một khóa có thể theo sau bởi NHIỀU mảng con
     * ("Child iterators", "Result processors profile"). Các cặp khóa=giá trị vô hướng liền nhau gộp thành một dòng
     * (một iterator hoặc một result processor), mảng con in thụt vào một cấp.
     */
    private static void flatten(Object node, int depth, List<String> out) {
        String indent = "  ".repeat(depth);
        if (!(node instanceof List<?> items)) {
            out.add(indent + str(node));
            return;
        }
        List<String> line = new ArrayList<>();
        int i = 0;
        while (i < items.size()) {
            Object item = items.get(i);
            if (item instanceof List<?>) {
                flushLine(indent, line, out);
                flatten(item, depth + 1, out);
                i++;
                continue;
            }
            String key = str(item);
            if (i + 1 >= items.size()) {
                line.add(key);
                i++;
                continue;
            }
            Object next = items.get(i + 1);
            if (next == null) {
                line.add(key + "=(none)");
                i += 2;
            } else if (!(next instanceof List<?> nested)) {
                line.add(key + "=" + str(next));
                i += 2;
            } else if (nested.isEmpty()) {
                line.add(key + "=[]");
                i += 2;
            } else {
                flushLine(indent, line, out);
                out.add(indent + key + ":");
                i++;
                while (i < items.size() && items.get(i) instanceof List<?>) {
                    flatten(items.get(i), depth + 1, out);
                    i++;
                }
            }
        }
        flushLine(indent, line, out);
    }

    private static void flushLine(String indent, List<String> line, List<String> out) {
        if (!line.isEmpty()) {
            out.add(indent + String.join(", ", line));
            line.clear();
        }
    }
}
