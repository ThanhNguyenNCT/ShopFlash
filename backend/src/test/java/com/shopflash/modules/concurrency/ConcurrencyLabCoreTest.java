package com.shopflash.modules.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.lab.AttemptOutcome;
import com.shopflash.modules.concurrency.lab.AttemptOutcome.Result;
import com.shopflash.modules.concurrency.lab.LabErrorCodes;
import com.shopflash.modules.concurrency.lab.LabRunner;
import com.shopflash.modules.concurrency.lab.LabRunner.RawRun;
import com.shopflash.modules.concurrency.lab.LabRunner.Sample;
import com.shopflash.modules.concurrency.lab.LabStats;
import com.shopflash.modules.concurrency.lab.LabStats.RunMetrics;
import com.shopflash.modules.concurrency.lab.LabStats.Stat;

/** Bộ chạy và thống kê của Concurrency Lab, không cần Oracle, Redis: ./mvnw test -Dtest='ConcurrencyLabCoreTest' */
class ConcurrencyLabCoreTest {

    private static final long MS = 1_000_000;

    @Test
    void runnerNeverExceedsConcurrencyLimitAndRunsEveryBuyer() throws InterruptedException {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();

        RawRun raw = LabRunner.run(40, 4, () -> {
            int now = active.incrementAndGet();
            maxActive.accumulateAndGet(now, Math::max);
            calls.incrementAndGet();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            active.decrementAndGet();
            return AttemptOutcome.success(0);
        });

        assertThat(calls.get()).isEqualTo(40);
        assertThat(raw.samples()).hasSize(40);
        assertThat(maxActive.get()).isEqualTo(4);
        // 40 lượt, mỗi lần 4 lượt, mỗi lượt 20 ms: ít nhất 10 đợt.
        assertThat(raw.elapsedNanos()).isGreaterThanOrEqualTo(200 * MS);
    }

    @Test
    void runnerTurnsExceptionsIntoErrorCodes() throws InterruptedException {
        AtomicInteger n = new AtomicInteger();
        RawRun raw = LabRunner.run(3, 3, () -> {
            int i = n.getAndIncrement();
            if (i == 0) {
                throw new CannotAcquireLockException("khóa", new SQLException("busy", "61000", 54));
            }
            if (i == 1) {
                throw ApiException.conflict("LAB_NOT_RESET", "chưa reset");
            }
            throw new IllegalStateException("lạ");
        });

        assertThat(raw.samples()).extracting(s -> s.outcome().result()).containsOnly(Result.ERROR);
        assertThat(raw.samples()).extracting(s -> s.outcome().errorCode())
                .containsExactlyInAnyOrder("ORA-00054", "LAB_NOT_RESET", "IllegalStateException");
    }

    @Test
    void oracleErrorCodeIsFoundDeepInTheCauseChain() {
        RuntimeException wrapped = new RuntimeException(new CannotAcquireLockException("x",
                new SQLException("ORA-08177: can't serialize access", "72000", 8177)));
        assertThat(LabErrorCodes.oracleCode(wrapped)).isEqualTo(8177);
        assertThat(LabErrorCodes.of(wrapped)).isEqualTo("ORA-08177");
    }

    @Test
    void metricsCountOversellAndLostUpdates() {
        // 3 lượt báo thành công nhưng bộ đếm chỉ tăng 2, quota 2: bán vượt 1, mất 1 cập nhật.
        List<Sample> samples = List.of(
                new Sample(AttemptOutcome.success(0), 1 * MS),
                new Sample(AttemptOutcome.success(2), 2 * MS),
                new Sample(AttemptOutcome.success(0), 3 * MS),
                new Sample(AttemptOutcome.soldOut(1), 4 * MS),
                new Sample(AttemptOutcome.error("ORA-00054", 0), 5 * MS),
                new Sample(AttemptOutcome.error("ORA-00054", 0), 6 * MS));
        RunMetrics m = LabStats.measure(1, 2, new RawRun(samples, 600 * MS), 2);

        assertThat(m.successes()).isEqualTo(3);
        assertThat(m.soldOut()).isEqualTo(1);
        assertThat(m.errors()).isEqualTo(2);
        assertThat(m.errorCodes()).isEqualTo(Map.of("ORA-00054", 2));
        assertThat(m.retries()).isEqualTo(3);
        assertThat(m.oversold()).isEqualTo(1);
        assertThat(m.lostUpdates()).isEqualTo(1);
        assertThat(m.elapsedMs()).isEqualTo(600.0);
        assertThat(m.throughput()).isEqualTo(10.0);
        assertThat(m.p50Ms()).isEqualTo(3.0);
        assertThat(m.p99Ms()).isEqualTo(6.0);
    }

    @Test
    void percentilesUseNearestRank() {
        List<Sample> samples = new ArrayList<>();
        for (int ms = 100; ms >= 1; ms--) {
            samples.add(new Sample(AttemptOutcome.success(0), ms * MS));
        }
        RunMetrics m = LabStats.measure(1, 100, new RawRun(samples, 1000 * MS), 100);

        assertThat(m.p50Ms()).isEqualTo(50.0);
        assertThat(m.p95Ms()).isEqualTo(95.0);
        assertThat(m.p99Ms()).isEqualTo(99.0);
        assertThat(m.oversold()).isZero();
    }

    @Test
    void summaryUsesSampleStandardDeviation() {
        List<RunMetrics> runs = new ArrayList<>();
        int run = 1;
        for (int successes : new int[] {2, 4, 4, 4, 5, 5, 7, 9}) {
            List<Sample> samples = new ArrayList<>();
            for (int i = 0; i < successes; i++) {
                samples.add(new Sample(AttemptOutcome.success(0), MS));
            }
            runs.add(LabStats.measure(run++, 100, new RawRun(samples, 10 * MS), successes));
        }
        Stat successes = LabStats.summarize(runs).successes();

        assertThat(successes.mean()).isEqualTo(5.0);
        assertThat(successes.sd()).isCloseTo(Math.sqrt(32.0 / 7), within(0.001));
        assertThat(LabStats.summarize(runs.subList(0, 1)).successes().sd()).isZero();
    }
}
