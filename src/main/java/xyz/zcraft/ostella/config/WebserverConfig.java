package xyz.zcraft.ostella.config;

public record WebserverConfig(
        int port,
        int maxThreads,
        int minThreads,
        int idleTimeout
) {
}
