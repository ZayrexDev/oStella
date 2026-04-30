package xyz.zcraft.config;

public record ScoreRenderConfig(
        boolean enabled,
        String danserPath,
        String configPath,
        int renderQueueSize,
        int renderThreads
) {
}
