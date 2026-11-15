package com.shopflash.modules.concurrency.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Chạy N lượt mua, mỗi lượt một virtual thread, tối đa C lượt chạy cùng lúc (Semaphore). Mọi lượt chờ ở một
 * cổng xuất phát chung (CountDownLatch) rồi mới bắt đầu, để tranh chấp xảy ra ngay từ đầu.
 *
 * Độ trễ một lượt đo từ lúc lấy được permit tới lúc xong, nên gồm cả thời gian chờ kết nối từ pool Oracle
 * hoặc Redis khi C lớn hơn kích thước pool; không gồm thời gian xếp hàng chờ permit.
 *
 * Mỗi lần gọi run sở hữu executor của nó: trả về thì mọi lượt đã xong. Bị ngắt (interrupt) thì ngắt các lượt
 * đang chạy, đợi chúng dừng rồi ném InterruptedException.
 */
public final class LabRunner {

    private LabRunner() {
    }

    /** Một lượt: kết quả và thời gian chạy (nano giây). */
    public record Sample(AttemptOutcome outcome, long nanos) {
    }

    /** Kết quả thô một lần chạy, samples theo thứ tự lượt. */
    public record RawRun(List<Sample> samples, long elapsedNanos) {
    }

    public static RawRun run(int buyers, int concurrency, Supplier<AttemptOutcome> attempt)
            throws InterruptedException {
        if (buyers < 1 || concurrency < 1) {
            throw new IllegalArgumentException("buyers và concurrency phải >= 1");
        }
        Semaphore permits = new Semaphore(concurrency);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Sample>> futures = new ArrayList<>(buyers);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int i = 0; i < buyers; i++) {
                    futures.add(executor.submit(() -> buyOnce(gate, permits, attempt)));
                }
                long started = System.nanoTime();
                gate.countDown();
                List<Sample> samples = new ArrayList<>(buyers);
                for (Future<Sample> future : futures) {
                    samples.add(future.get());
                }
                return new RawRun(samples, System.nanoTime() - started);
            } catch (InterruptedException | RuntimeException e) {
                executor.shutdownNow();
                throw e;
            } catch (ExecutionException e) {
                // buyOnce đã bắt mọi RuntimeException; tới đây chỉ còn lượt bị ngắt hoặc Error.
                executor.shutdownNow();
                if (e.getCause() instanceof Error err) {
                    throw err;
                }
                throw new IllegalStateException("Một lượt mua dừng bất thường", e.getCause());
            }
        }
    }

    private static Sample buyOnce(CountDownLatch gate, Semaphore permits, Supplier<AttemptOutcome> attempt)
            throws InterruptedException {
        gate.await();
        permits.acquire();
        try {
            long t0 = System.nanoTime();
            AttemptOutcome outcome;
            try {
                outcome = attempt.get();
            } catch (RuntimeException e) {
                outcome = AttemptOutcome.error(LabErrorCodes.of(e), 0);
            }
            return new Sample(outcome, System.nanoTime() - t0);
        } finally {
            permits.release();
        }
    }
}
