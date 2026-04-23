package xyz.zcraft.config;

public record OstellaConfig(
        int maxThreads,
        int requestDelayMs,
        boolean debugMode
) {
}
