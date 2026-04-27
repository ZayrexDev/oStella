package xyz.zcraft.config;

public record OstellaConfig(
        int maxThreads,
        int requestPerSecond,
        boolean debugMode
) {
}
