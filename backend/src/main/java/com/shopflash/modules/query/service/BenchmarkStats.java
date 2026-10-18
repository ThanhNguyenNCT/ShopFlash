package com.shopflash.modules.query.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thống kê trên các mẫu thời gian (ms). Độ lệch chuẩn mẫu (chia n-1, n=1 thì 0);
 * phân vị theo nearest-rank: phần tử thứ ceil(p/100 * n) của dãy đã sắp xếp. Kết quả làm tròn 2 chữ số.
 */
public record BenchmarkStats(double mean, double stdDev, double p50, double p95) {

    public static BenchmarkStats of(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một mẫu");
        }
        int n = samples.size();
        double sum = 0;
        for (double s : samples) {
            sum += s;
        }
        double mean = sum / n;
        double sq = 0;
        for (double s : samples) {
            sq += (s - mean) * (s - mean);
        }
        double sd = n > 1 ? Math.sqrt(sq / (n - 1)) : 0;
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        return new BenchmarkStats(round(mean), round(sd), percentile(sorted, 50), percentile(sorted, 95));
    }

    /** Nearest-rank trên dãy đã sắp xếp. */
    public static double percentile(List<Double> sorted, int p) {
        int rank = (p * sorted.size() + 99) / 100; // ceil(p*n/100) bằng số nguyên, tránh sai số dấu phẩy động
        return round(sorted.get(Math.max(rank, 1) - 1));
    }

    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
