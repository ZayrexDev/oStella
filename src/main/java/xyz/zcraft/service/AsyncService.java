package xyz.zcraft.service;

import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

public class AsyncService {
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    @SuppressWarnings("UnstableApiUsage")
    private final RateLimiter rateLimiter;

    public AsyncService(int threads, int requestPerSecond) {
        executor = Executors.newFixedThreadPool(Math.max(threads, 1));
        scheduler = Executors.newSingleThreadScheduledExecutor();
        //noinspection UnstableApiUsage
        this.rateLimiter = RateLimiter.create(requestPerSecond);
    }

    public <T> CompletableFuture<T> enqueueAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            //noinspection UnstableApiUsage
            rateLimiter.acquire();
            return supplier.get();
        }, executor);
    }

    public void close() {
        scheduler.shutdown();
        executor.shutdown();
    }
}
