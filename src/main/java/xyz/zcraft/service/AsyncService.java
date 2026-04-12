package xyz.zcraft.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class AsyncService {
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final long delayMillis;

    public AsyncService(int threads, long delayMillis) {
        executor = Executors.newFixedThreadPool(Math.max(threads, 1));
        scheduler = Executors.newSingleThreadScheduledExecutor();
        this.delayMillis = Math.max(delayMillis, 0L);
    }

    public <T> CompletableFuture<T> runAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public <T> CompletableFuture<T> runDelayAsync(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();

        scheduler.schedule(() -> executor.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        }), delayMillis, TimeUnit.MILLISECONDS);

        return future;
    }

    public void close() {
        scheduler.shutdown();
        executor.shutdown();
    }
}
