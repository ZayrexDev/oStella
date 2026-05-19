package xyz.zcraft.ostella.config;

public record ScoreRenderConfig(
        boolean enabled,
        String danserPath,
        String configPath,
        int renderQueueSize,
        int renderThreads
) {
}
