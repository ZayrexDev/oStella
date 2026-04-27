package xyz.zcraft.config;

public record OstellaConfig(
        int requestPerSecond,
        boolean debugMode
) {
}
