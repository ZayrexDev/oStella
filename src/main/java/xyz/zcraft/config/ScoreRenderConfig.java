package xyz.zcraft.config;

public record ScoreRenderConfig(
        boolean enabled,
        String danserPath,
        int renderQueueSize,
        int renderThreads
) {
}
