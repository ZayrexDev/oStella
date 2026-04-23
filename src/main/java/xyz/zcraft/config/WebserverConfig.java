package xyz.zcraft.config;

public record WebserverConfig(
        int port,
        int maxThreads,
        int minThreads,
        int idleTimeout
) {
}
