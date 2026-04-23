package xyz.zcraft.config;

public record AppConfig(
        OstellaConfig ostella,
        OsuConfig osu,
        ScoreRenderConfig replayRender,
        WebserverConfig webserver
) {
}

