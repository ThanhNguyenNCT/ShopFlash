package com.shopflash.modules.query.service;

import com.shopflash.modules.query.dto.response.CompareResponse;
import com.shopflash.modules.query.dto.response.Mismatch;
import com.shopflash.modules.query.dto.response.PageInfo;
import com.shopflash.modules.query.dto.response.ReportData;
import com.shopflash.modules.query.dto.response.ReportResponse;
import com.shopflash.modules.query.dto.response.ReportRow;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Chạy cùng một ReportQuery trên Oracle rồi Redis (tuần tự, để hai lần đo không tranh nhau tài nguyên),
 * so từng dòng theo ReportRow.key() và từng trường của record.
 *
 * <p>Các loại mismatch:
 * <ul>
 *   <li>field = tên trường record (vd. revenue): giá trị khác nhau; tiền (BigDecimal) so sau khi làm tròn 2 chữ số.</li>
 *   <li>field = "row": khóa chỉ có ở một bên; bên thiếu là null, bên kia là cả dòng.</li>
 *   <li>field = "position": hai bên cùng tập khóa nhưng một khóa nằm khác vị trí (thứ tự sắp xếp khác),
 *       giá trị là vị trí tính từ 1.</li>
 *   <li>key = "page" / "summary": khác ở page.total hoặc summary (vd. summary.averageSpent).</li>
 * </ul>
 * mismatches giữ tối đa 5 phần tử đầu; match = false nếu có ít nhất một khác biệt.
 */
@Service
public class CompareService {

    public static final int MAX_MISMATCHES = 5;

    private final ReportService reportService;

    public CompareService(ReportService reportService) {
        this.reportService = reportService;
    }

    public CompareResponse compare(ReportQuery query) {
        ReportResponse oracle = reportService.run(query, Source.ORACLE);
        ReportResponse redis = reportService.run(query, Source.REDIS);
        if (!oracle.meta().supported() || !redis.meta().supported()) {
            return new CompareResponse(query.report().slug(), oracle, redis, null, List.of());
        }
        List<Mismatch> all = diff(oracle.data(), redis.data());
        List<Mismatch> limited = all.size() > MAX_MISMATCHES ? List.copyOf(all.subList(0, MAX_MISMATCHES)) : all;
        return new CompareResponse(query.report().slug(), oracle, redis, all.isEmpty(), limited);
    }

    /** Liệt kê mọi khác biệt giữa hai ReportData (không cắt). */
    public static List<Mismatch> diff(ReportData oracle, ReportData redis) {
        List<Mismatch> out = new ArrayList<>();
        Map<String, ReportRow> o = index(oracle);
        Map<String, ReportRow> r = index(redis);

        List<String> oKeys = new ArrayList<>(o.keySet());
        List<String> rKeys = new ArrayList<>(r.keySet());
        // Chỉ báo lệch thứ tự khi hai bên cùng tập khóa; thiếu dòng thì vị trí lệch theo là hệ quả, không phải lỗi riêng.
        boolean sameKeys = o.keySet().equals(r.keySet());
        for (int i = 0; i < oKeys.size(); i++) {
            String key = oKeys.get(i);
            ReportRow redisRow = r.get(key);
            if (redisRow == null) {
                out.add(new Mismatch(key, "row", o.get(key), null));
                continue;
            }
            compareRows(key, o.get(key), redisRow, out);
            int redisPos = rKeys.indexOf(key);
            if (sameKeys && redisPos != i) {
                out.add(new Mismatch(key, "position", i + 1, redisPos + 1));
            }
        }
        for (String key : rKeys) {
            if (!o.containsKey(key)) {
                out.add(new Mismatch(key, "row", null, r.get(key)));
            }
        }

        PageInfo op = oracle == null ? null : oracle.page();
        PageInfo rp = redis == null ? null : redis.page();
        if (op != null && rp != null && op.total() != rp.total()) {
            out.add(new Mismatch("page", "total", op.total(), rp.total()));
        }
        Object os = oracle == null ? null : oracle.summary();
        Object rs = redis == null ? null : redis.summary();
        if (os != null && rs != null && os.getClass() == rs.getClass() && os.getClass().isRecord()) {
            compareRecords("summary", os, rs, out);
        } else if (!Objects.equals(os, rs)) {
            out.add(new Mismatch("summary", "summary", os, rs));
        }
        return out;
    }

    private static Map<String, ReportRow> index(ReportData data) {
        Map<String, ReportRow> map = new LinkedHashMap<>();
        if (data != null && data.rows() != null) {
            for (ReportRow row : data.rows()) {
                map.putIfAbsent(row.key(), row);
            }
        }
        return map;
    }

    private static void compareRows(String key, ReportRow oracle, ReportRow redis, List<Mismatch> out) {
        if (oracle.getClass() != redis.getClass()) {
            out.add(new Mismatch(key, "row", oracle, redis));
            return;
        }
        compareRecords(key, oracle, redis, out);
    }

    private static void compareRecords(String key, Object oracle, Object redis, List<Mismatch> out) {
        for (RecordComponent c : oracle.getClass().getRecordComponents()) {
            Object ov = normalize(read(c, oracle));
            Object rv = normalize(read(c, redis));
            if (!valuesEqual(ov, rv)) {
                out.add(new Mismatch(key, c.getName(), ov, rv));
            }
        }
    }

    private static Object read(RecordComponent c, Object target) {
        try {
            return c.getAccessor().invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Không đọc được trường " + c.getName(), e);
        }
    }

    private static Object normalize(Object value) {
        return value instanceof BigDecimal bd ? bd.setScale(2, RoundingMode.HALF_UP) : value;
    }

    private static boolean valuesEqual(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }
}
