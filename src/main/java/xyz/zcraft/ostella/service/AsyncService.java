package xyz.zcraft.ostella.service;

import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class AsyncService {
    private final ExecutorService executor;

    @SuppressWarnings("UnstableApiUsage")
    private final RateLimiter rateLimiter;

    public AsyncService(int requestPerSecond) {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        //noinspection UnstableApiUsage
        this.rateLimiter = RateLimiter.create(Math.max(1, requestPerSecond));
    }

    public <T> CompletableFuture<T> enqueueAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            //noinspection UnstableApiUsage
            rateLimiter.acquire();
            return supplier.get();
        }, executor);
    }

    public void close() {
        executor.shutdown();
    }
}
