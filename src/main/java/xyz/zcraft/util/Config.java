package xyz.zcraft.util;

import io.github.cdimascio.dotenv.Dotenv;

public record Config(String clientId, String clientSecret, String port, String maxThreads, String delay) {
    public static Config fromEnv() {
        Dotenv dotenv = Dotenv.load();
        return new Config(
                dotenv.get("OSU_CLIENT_ID"), dotenv.get("OSU_CLIENT_SECRET"),
                dotenv.get("OSU_PORT"), dotenv.get("OSU_MAX_THREADS"), dotenv.get("OSU_DELAY_MILLIS")
        );
    }
}
