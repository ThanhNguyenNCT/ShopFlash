package com.shopflash.modules.storage.service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse;
import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse.FieldDiff;
import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse.Issue;
import com.shopflash.modules.storage.model.CatalogProduct;
import com.shopflash.modules.storage.repository.ProductProjectionRepository;
import com.shopflash.modules.storage.repository.ProductProjectionRepository.StoredDocument;
import com.shopflash.modules.storage.repository.StorageRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Đối chiếu product projection Redis với kỳ vọng tính từ Oracle, theo từng Product ID và từng field. Kỳ vọng dùng
 * chung {@link ProductProjectionRepository#fields(CatalogProduct)} với lúc ghi. Chỉ đọc, không sửa dữ liệu.
 *
 * <p>Oracle và Redis không chung snapshot: Oracle được đọc trước rồi mới tới Redis, nên một thay đổi đang diễn ra
 * có thể hiện thành sai lệch tạm thời. Kiểm tra lại sau vài giây để phân biệt với sai lệch thật.
 */
@Service
public class ProjectionConsistencyChecker {

    private static final Logger log = LoggerFactory.getLogger(ProjectionConsistencyChecker.class);
    static final int BATCH_SIZE = 500;
    static final int ISSUE_LIMIT = 50;
    private static final String KEY_PREFIX = "product:";

    private final StorageRepository repository;
    private final ProductProjectionRepository projection;
    private final ObjectMapper json;

    public ProjectionConsistencyChecker(StorageRepository repository, ProductProjectionRepository projection,
            ObjectMapper json) {
        this.repository = repository;
        this.projection = projection;
        this.json = json;
    }

    public ProjectionStatusResponse check() {
        OffsetDateTime checkedAt = OffsetDateTime.now();
        long started = System.nanoTime();
        try {
            long categories = repository.countCategories(null, null);
            long oracleProducts = repository.countProducts(null, null, null);
            Map<Long, Map<String, Object>> expected = new TreeMap<>();
            for (long afterId = 0; ; ) {
                List<CatalogProduct> batch = repository.findProjectionBatch(afterId, BATCH_SIZE);
                if (batch.isEmpty()) break;
                batch.forEach(p -> expected.put(p.product().productId(), ProductProjectionRepository.fields(p)));
                afterId = batch.getLast().product().productId();
            }

            List<String> keys = projection.keys();
            Tally tally = new Tally();
            for (int from = 0; from < keys.size(); from += BATCH_SIZE) {
                for (StoredDocument doc : projection.read(keys.subList(from, Math.min(keys.size(), from + BATCH_SIZE)))) {
                    classify(doc, expected, tally);
                }
            }
            // Còn lại trong expected là Product Oracle chưa có document (kể cả key bị xóa trong lúc đang đọc).
            for (Long id : expected.keySet()) {
                tally.missing++;
                tally.add(new Issue("MISSING", id, ProductProjectionRepository.key(id), "Chưa có document Redis", List.of()));
            }
            boolean inSync = tally.missing == 0 && tally.extra == 0 && tally.mismatched == 0 && tally.invalid == 0;
            return new ProjectionStatusResponse(ProjectionStatusResponse.COMPLETED, null, null, checkedAt, elapsed(started),
                    categories, oracleProducts, (long) keys.size(), tally.matched, tally.missing, tally.extra,
                    tally.mismatched, tally.invalid, inSync, ISSUE_LIMIT, tally.issues);
        } catch (DataAccessException e) {
            log.warn("Không đọc được Oracle khi kiểm tra product projection", e);
            return failed(checkedAt, started, "ORACLE_UNAVAILABLE", "Không đọc được Oracle, chưa xác định trạng thái đồng bộ");
        } catch (ApiException e) {
            if (!"REDIS_UNAVAILABLE".equals(e.code())) throw e;
            log.warn("Không đọc được Redis khi kiểm tra product projection", e);
            return failed(checkedAt, started, "REDIS_UNAVAILABLE", "Không đọc được Redis, chưa xác định trạng thái đồng bộ");
        }
    }

    private void classify(StoredDocument doc, Map<Long, Map<String, Object>> expected, Tally tally) {
        Long id = idOf(doc.key());
        if (id == null) {
            tally.invalid++;
            tally.add(new Issue("INVALID", null, doc.key(), "Key không đúng dạng product:{productId}", List.of()));
            return;
        }
        Map<String, Object> want = expected.remove(id);
        if (want == null) {
            // Key vừa bị xóa giữa SCAN và JSON.GET thì không còn là document thừa.
            if (doc.json() == null && doc.error() == null) return;
            tally.extra++;
            tally.add(new Issue("EXTRA", id, doc.key(), "Không có Product Oracle tương ứng", List.of()));
            return;
        }
        if (doc.json() == null && doc.error() == null) {
            expected.put(id, want); // đếm là MISSING ở cuối
            return;
        }
        String problem = doc.error() != null ? "Redis không đọc được key: " + doc.error() : null;
        JsonNode node = null;
        if (problem == null) {
            try {
                node = json.readTree(doc.json());
            } catch (JacksonException e) {
                problem = "Không phải JSON hợp lệ";
            }
        }
        if (problem == null) problem = structureProblem(node);
        if (problem != null) {
            tally.invalid++;
            tally.add(new Issue("INVALID", id, doc.key(), problem, List.of()));
            return;
        }
        List<FieldDiff> diffs = diff(want, node);
        if (diffs.isEmpty()) {
            tally.matched++;
        } else {
            tally.mismatched++;
            tally.add(new Issue("MISMATCH", id, doc.key(), "Khác Oracle ở " + diffs.size() + " field", diffs));
        }
    }

    /** null nếu document có đúng các field của schema, mỗi field đúng kiểu JSON. */
    static String structureProblem(JsonNode node) {
        if (node == null || !node.isObject()) return "Document không phải JSON object";
        for (String name : node.propertyNames()) {
            if (!FieldKind.SCHEMA.containsKey(name)) return "Field ngoài schema: " + name;
        }
        for (var entry : FieldKind.SCHEMA.entrySet()) {
            JsonNode value = node.get(entry.getKey());
            if (value == null) return "Thiếu field " + entry.getKey();
            if (!entry.getValue().accepts(value)) return "Field " + entry.getKey() + " sai kiểu (" + value.getNodeType() + ")";
        }
        return null;
    }

    /** So theo giá trị: số nguyên theo long, giá theo BigDecimal.compareTo (1000 = 1000.00), chuỗi và boolean bằng nhau. */
    public static List<FieldDiff> diff(Map<String, Object> expected, JsonNode node) {
        List<FieldDiff> diffs = new ArrayList<>();
        for (var entry : FieldKind.SCHEMA.entrySet()) {
            String name = entry.getKey();
            Object want = expected.get(name);
            JsonNode got = node.get(name);
            boolean same = switch (entry.getValue()) {
                case INTEGER -> want instanceof Number n && got.longValue() == n.longValue();
                case DECIMAL -> want instanceof BigDecimal d && got.decimalValue().compareTo(d) == 0;
                case BOOLEAN -> Objects.equals(want, got.booleanValue());
                case TEXT, NULLABLE_TEXT -> Objects.equals(want, got.isNull() ? null : got.stringValue());
            };
            if (!same) diffs.add(new FieldDiff(name, want, got));
        }
        return diffs;
    }

    static Long idOf(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) return null;
        String digits = key.substring(KEY_PREFIX.length());
        if (digits.isEmpty() || digits.length() > 18 || !digits.chars().allMatch(Character::isDigit)) return null;
        return Long.parseLong(digits);
    }

    private static ProjectionStatusResponse failed(OffsetDateTime checkedAt, long started, String code, String message) {
        return new ProjectionStatusResponse(ProjectionStatusResponse.FAILED, code, message, checkedAt, elapsed(started),
                null, null, null, null, null, null, null, null, false, ISSUE_LIMIT, List.of());
    }

    private static long elapsed(long started) {
        return Math.round((System.nanoTime() - started) / 1_000_000.0);
    }

    /** Kiểu JSON của từng field trong schema projection (AGENTS.md của module). */
    enum FieldKind {
        INTEGER, DECIMAL, BOOLEAN, TEXT, NULLABLE_TEXT;

        static final Map<String, FieldKind> SCHEMA = schema();

        private static Map<String, FieldKind> schema() {
            Map<String, FieldKind> s = new LinkedHashMap<>();
            s.put("id", INTEGER);
            s.put("sku", TEXT);
            s.put("name", TEXT);
            s.put("categoryId", INTEGER);
            s.put("categoryName", TEXT);
            s.put("price", DECIMAL);
            s.put("status", TEXT);
            s.put("inStock", BOOLEAN);
            s.put("thumbnailUrl", NULLABLE_TEXT);
            return Collections.unmodifiableMap(s);
        }

        boolean accepts(JsonNode value) {
            return switch (this) {
                case INTEGER -> value.isIntegralNumber();
                case DECIMAL -> value.isNumber();
                case BOOLEAN -> value.isBoolean();
                case TEXT -> value.isString();
                case NULLABLE_TEXT -> value.isString() || value.isNull();
            };
        }
    }

    private static final class Tally {
        long matched;
        long missing;
        long extra;
        long mismatched;
        long invalid;
        final List<Issue> issues = new ArrayList<>();

        void add(Issue issue) {
            if (issues.size() < ISSUE_LIMIT) issues.add(issue);
        }
    }
}
