package xyz.zcraft.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

public class AsyncHelper {
    private final ThreadPoolExecutor executor;

    public AsyncHelper(int threads) {
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threads);
    }

    public <T> CompletableFuture<T> runAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public void close() {
        if (executor != null) executor.shutdown();
    }
}
