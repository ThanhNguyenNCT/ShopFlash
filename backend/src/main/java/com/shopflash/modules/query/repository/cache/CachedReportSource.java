package com.shopflash.modules.query.repository.cache;

import com.shopflash.modules.query.dto.response.PlanResponse;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.Source;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.ReportSource;
import com.shopflash.modules.query.repository.oracle.OracleReportSource;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Nguồn "cache" (cache-aside): Oracle tính, Redis giữ kết quả.
 * GET key trên Redis; có thì trả luôn (HIT, 1 round-trip). Không có thì chạy Oracle, lưu kết quả với TTL rồi trả
 * (MISS). Không xóa cache khi đơn hàng đổi: dữ liệu có thể cũ tới hết TTL, đó là cái giá của cách này.
 * Redis lỗi thì vẫn trả kết quả Oracle, chỉ ghi chú trong meta.note.
 */
@Repository
public class CachedReportSource implements ReportSource {

    public static final String KEY_PREFIX = "report:cache:";
    private static final Logger log = LoggerFactory.getLogger(CachedReportSource.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final ReportSource oracle;
    private final ReportCacheStore store;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public CachedReportSource(OracleReportSource oracle, ReportCacheStore store,
            @Value("${shopflash.query.cache-ttl:PT5M}") Duration ttl) {
        this(oracle, store, ttl, Clock.system(ReportQuery.ZONE));
    }

    public CachedReportSource(ReportSource oracle, ReportCacheStore store, Duration ttl, Clock clock) {
        this.oracle = oracle;
        this.store = store;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public Source source() {
        return Source.CACHE;
    }

    /** Mọi tham số làm đổi kết quả đều nằm trong key. */
    public static String key(ReportQuery q) {
        return KEY_PREFIX + q.report().slug() + ":" + q.from() + ":" + q.to() + ":" + q.joinHint()
                + ":limit=" + q.limit() + ":page=" + q.page() + ":size=" + q.size();
    }

    @Override
    public SourceResult run(ReportQuery query) {
        String key = key(query);
        String redisProblem = null;
        try {
            Optional<String> cached = store.get(key);
            if (cached.isPresent()) {
                ReportCacheCodec.Entry entry = ReportCacheCodec.decode(query.report(), cached.get());
                return new SourceResult(entry.data(), "HIT", 1, "GET " + key, true,
                        "Kết quả Oracle lưu lúc " + entry.cachedAt().atZoneSameInstant(ReportQuery.ZONE).format(TIME)
                                + ", TTL " + ttl.toSeconds() + " s. Đơn mới sau thời điểm đó chưa có trong kết quả này.");
            }
        } catch (RuntimeException e) {
            log.warn("Không đọc được cache báo cáo {}, chạy thẳng Oracle: {}", key, e.toString());
            redisProblem = "Redis lỗi khi đọc cache";
        }

        SourceResult fresh = oracle.run(query);
        if (fresh.supported() && redisProblem == null) {
            try {
                store.put(key, ReportCacheCodec.encode(fresh.data(), now()), ttl);
            } catch (RuntimeException e) {
                log.warn("Không ghi được cache báo cáo {}: {}", key, e.toString());
                redisProblem = "Redis lỗi khi ghi cache";
            }
        }
        String statement = "GET " + key + "\n-- chưa có: chạy Oracle\n" + fresh.statement()
                + "\nSET " + key + " <kết quả JSON> PX " + ttl.toMillis();
        String note = redisProblem != null
                ? redisProblem + ", trả kết quả Oracle (không lưu cache)."
                : "Chưa có trong cache: chạy Oracle rồi lưu vào Redis, TTL " + ttl.toSeconds() + " s.";
        return new SourceResult(fresh.data(), "MISS", fresh.roundTrips() + 2, statement, fresh.supported(), note);
    }

    @Override
    public PlanResponse plan(ReportQuery query) {
        return oracle.plan(query);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock.withZone(ReportQuery.ZONE)).truncatedTo(ChronoUnit.SECONDS);
    }
}
