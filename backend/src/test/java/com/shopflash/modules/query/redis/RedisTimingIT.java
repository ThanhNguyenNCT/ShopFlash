package com.shopflash.modules.query.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.RedisMode;
import com.shopflash.modules.query.model.Report;
import com.shopflash.modules.query.model.ReportQuery;
import com.shopflash.modules.query.model.SourceResult;
import com.shopflash.modules.query.repository.redis.RedisReportSource;

/**
 * Đo thời gian từng báo cáo trên read model hiện có (không rebuild): chạy tuần tự, 1 lần warm-up, median của 5.
 * Chạy: ./mvnw -q test -Dtest=RedisTimingIT
 */
@SpringBootTest // MOCK: SecurityConfig cần context web (HttpSecurity)
class RedisTimingIT {

    @Autowired
    RedisReportSource redis;

    @Test
    void medianOfFive() {
        LocalDate to = LocalDate.now(ReportQuery.ZONE);
        LocalDate from = to.minusMonths(12).withDayOfMonth(1);
        for (RedisMode mode : RedisMode.values()) {
            for (Report report : List.of(Report.REVENUE_BY_MONTH, Report.REVENUE_BY_CATEGORY, Report.TOP_PRODUCTS,
                    Report.VIP_CUSTOMERS)) {
                ReportQuery q = new ReportQuery(report, from, to, JoinHint.AUTO, mode, 10, 1, 20);
                SourceResult warm = redis.run(q);
                assertThat(warm.supported()).isTrue();
                double[] ms = new double[5];
                for (int i = 0; i < ms.length; i++) {
                    long t0 = System.nanoTime();
                    redis.run(q);
                    ms[i] = (System.nanoTime() - t0) / 1_000_000.0;
                }
                double[] sorted = ms.clone();
                Arrays.sort(sorted);
                System.out.printf("[redis-timing] %-9s %-20s median=%.1f ms samples=%s roundTrips=%d%n",
                        mode, report.slug(), sorted[2], Arrays.toString(ms), warm.roundTrips());
            }
        }
    }
}
