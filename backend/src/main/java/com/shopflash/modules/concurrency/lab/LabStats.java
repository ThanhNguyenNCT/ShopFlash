package com.shopflash.modules.concurrency.lab;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

import com.shopflash.modules.concurrency.lab.AttemptOutcome.Result;
import com.shopflash.modules.concurrency.lab.LabRunner.RawRun;
import com.shopflash.modules.concurrency.lab.LabRunner.Sample;

/**
 * Số đo một lần chạy và tổng hợp nhiều lần chạy.
 * - oversold = max(0, successes - quota): số người được báo mua thành công vượt số suất.
 * - lostUpdates = successes - finalSold: số lượt báo thành công nhưng bộ đếm không ghi nhận.
 * - Phân vị theo cách nearest-rank: phần tử thứ ceil(p/100 * n) trong dãy đã sắp xếp.
 * - Độ lệch chuẩn mẫu (chia n - 1); một lần chạy thì sd = 0.
 */
public final class LabStats {

    private LabStats() {
    }

    public record RunMetrics(
            int run,
            int successes,
            int soldOut,
            int errors,
            Map<String, Integer> errorCodes,
            int retries,
            long finalSold,
            long oversold,
            long lostUpdates,
            double elapsedMs,
            double throughput,
            double p50Ms,
            double p95Ms,
            double p99Ms) {
    }

    public record Stat(double mean, double sd) {
    }

    public record Summary(
            Stat successes,
            Stat oversold,
            Stat finalSold,
            Stat lostUpdates,
            Stat retries,
            Stat errors,
            Stat elapsedMs,
            Stat throughput,
            Stat p50Ms,
            Stat p95Ms,
            Stat p99Ms) {
    }

    public static RunMetrics measure(int run, int quota, RawRun raw, long finalSold) {
        int successes = 0;
        int soldOut = 0;
        int errors = 0;
        int retries = 0;
        Map<String, Integer> errorCodes = new TreeMap<>();
        long[] nanos = new long[raw.samples().size()];
        int i = 0;
        for (Sample sample : raw.samples()) {
            AttemptOutcome outcome = sample.outcome();
            retries += outcome.retries();
            if (outcome.result() == Result.SUCCESS) {
                successes++;
            } else if (outcome.result() == Result.SOLD_OUT) {
                soldOut++;
            } else {
                errors++;
                errorCodes.merge(outcome.errorCode(), 1, Integer::sum);
            }
            nanos[i++] = sample.nanos();
        }
        Arrays.sort(nanos);
        double elapsedMs = raw.elapsedNanos() / 1e6;
        double throughput = elapsedMs > 0 ? nanos.length / (elapsedMs / 1000) : 0;
        return new RunMetrics(run, successes, soldOut, errors, errorCodes, retries, finalSold,
                Math.max(0, successes - quota), successes - finalSold, round(elapsedMs, 3), round(throughput, 1),
                percentileMs(nanos, 50), percentileMs(nanos, 95), percentileMs(nanos, 99));
    }

    public static Summary summarize(List<RunMetrics> runs) {
        return new Summary(
                stat(runs, RunMetrics::successes),
                stat(runs, RunMetrics::oversold),
                stat(runs, RunMetrics::finalSold),
                stat(runs, RunMetrics::lostUpdates),
                stat(runs, RunMetrics::retries),
                stat(runs, RunMetrics::errors),
                stat(runs, RunMetrics::elapsedMs),
                stat(runs, RunMetrics::throughput),
                stat(runs, RunMetrics::p50Ms),
                stat(runs, RunMetrics::p95Ms),
                stat(runs, RunMetrics::p99Ms));
    }

    /** Phân vị p (0-100] của dãy nano giây đã sắp xếp tăng dần, đổi ra mili giây. */
    static double percentileMs(long[] sortedNanos, double p) {
        if (sortedNanos.length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(p / 100 * sortedNanos.length);
        int index = Math.min(sortedNanos.length - 1, Math.max(0, rank - 1));
        return round(sortedNanos[index] / 1e6, 3);
    }

    static Stat stat(List<RunMetrics> runs, ToDoubleFunction<RunMetrics> metric) {
        int n = runs.size();
        if (n == 0) {
            return new Stat(0, 0);
        }
        double sum = 0;
        for (RunMetrics run : runs) {
            sum += metric.applyAsDouble(run);
        }
        double mean = sum / n;
        if (n == 1) {
            return new Stat(round(mean, 3), 0);
        }
        double squares = 0;
        for (RunMetrics run : runs) {
            double d = metric.applyAsDouble(run) - mean;
            squares += d * d;
        }
        return new Stat(round(mean, 3), round(Math.sqrt(squares / (n - 1)), 3));
    }

    private static double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }
}
