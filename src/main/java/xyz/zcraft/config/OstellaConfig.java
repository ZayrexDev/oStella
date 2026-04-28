package xyz.zcraft.config;

public record OstellaConfig(
        int requestPerSecond,
        int renderWorkers,
        boolean debugMode
) {
}
