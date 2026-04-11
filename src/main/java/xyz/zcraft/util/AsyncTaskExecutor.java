package xyz.zcraft.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class AsyncTaskExecutor {
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    public AsyncTaskExecutor(int threads) {
        executor = Executors.newFixedThreadPool(Math.max(threads, 1));
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public <T> CompletableFuture<T> runAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    public <T> CompletableFuture<T> runAsync(Supplier<T> supplier, long delay) {
        long safeDelay = Math.max(delay, 0L);
        CompletableFuture<T> future = new CompletableFuture<>();

        scheduler.schedule(() -> executor.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        }), safeDelay, TimeUnit.MILLISECONDS);

        return future;
    }

    public CompletableFuture<Void> runAsync(Runnable runnable, long delay) {
        return runAsync(() -> {
            runnable.run();
            return null;
        }, delay);
    }

    public void enqueue(Runnable r, long delay) {
        runAsync(r, delay).exceptionally(rethrowUnchecked());
    }

    public void enqueue(Runnable r, long delay, Consumer<Throwable> onError) {
        runAsync(r, delay).exceptionally(e -> {
            onError.accept(e);
            return null;
        });
    }

    public void close() {
        scheduler.shutdown();
        executor.shutdown();
    }

    private Function<Throwable, Void> rethrowUnchecked() {
        return e -> {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
        };
    }
}
