package com.shopflash.modules.concurrency.lab;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Chạy N lượt mua trên đúng C thread (tối đa C lượt cùng lúc). Mọi lượt chờ ở một cổng xuất phát chung
 * (CountDownLatch) rồi mới bắt đầu, để tranh chấp xảy ra ngay từ đầu.
 *
 * Dùng platform thread, không dùng virtual thread: trên JDK 21–23, commons-pool2 (pool kết nối của Jedis) tạo kết
 * nối mới trong khối synchronized và Object.wait, nên ghim virtual thread vào carrier. Khi pool còn đang tăng kích
 * thước mà hàng trăm virtual thread cùng mượn kết nối, carrier bị chiếm hết và cả lần chạy đứng lại (đã gặp trong
 * test; thread dump dừng ở GenericObjectPool.create).
 *
 * Độ trễ một lượt đo từ lúc thread bắt đầu chạy lượt đó tới lúc xong, nên gồm cả thời gian chờ kết nối từ pool
 * Oracle hoặc Redis khi C lớn hơn kích thước pool; không gồm thời gian xếp hàng chờ thread.
 *
 * Mỗi lần gọi run sở hữu pool thread của nó: trả về thì mọi lượt đã xong và pool đã đóng. Bị ngắt (interrupt) thì
 * ngắt các lượt đang chạy, đợi chúng dừng rồi ném InterruptedException.
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
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Sample>> futures = new ArrayList<>(buyers);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), workers());
        executor.prestartAllCoreThreads();
        try (executor) {
            try {
                for (int i = 0; i < buyers; i++) {
                    futures.add(executor.submit(() -> buyOnce(gate, attempt)));
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

    private static Sample buyOnce(CountDownLatch gate, Supplier<AttemptOutcome> attempt) throws InterruptedException {
        gate.await();
        long t0 = System.nanoTime();
        AttemptOutcome outcome;
        try {
            outcome = attempt.get();
        } catch (RuntimeException e) {
            outcome = AttemptOutcome.error(LabErrorCodes.of(e), 0);
        }
        return new Sample(outcome, System.nanoTime() - t0);
    }

    private static ThreadFactory workers() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "cclab-buyer-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
