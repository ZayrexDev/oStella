package xyz.zcraft.ostella.config;

public record OstellaConfig(
        int requestPerSecond,
        int renderWorkers,
        boolean debugMode
) {
}
