package xyz.zcraft.util;

import io.github.cdimascio.dotenv.Dotenv;

public record Config(String clientId, String clientSecret, int port, int maxThreads, int delay, boolean debug) {
    public static Config fromEnv() throws IllegalArgumentException {
        final Dotenv env = Dotenv.load();
        return new Config(
                require(env, "OSU_CLIENT_ID"),
                require(env, "OSU_CLIENT_SECRET"),
                getInt(env, "OSTELLA_PORT", 8721),
                getInt(env, "OSTELLA_MAX_THREADS", 2),
                getInt(env, "OSTELLA_DELAY", 1000),
                getBool(env, "OSTELLA_DEBUG", false)
        );
    }

    private static String require(Dotenv env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env: " + key);
        }
        return value;
    }

    private static int requireInt(Dotenv env, String key) {
        return Integer.parseInt(env.get(key));
    }

    private static int getInt(Dotenv env, String key, int defaultValue) {
        try {
            return requireInt(env, key);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBool(Dotenv env, String key, boolean defaultValue) {
        final String s = env.get(key);

        if (s == null || s.isBlank()) return defaultValue;

        if (s.equals("true")) return true;
        if (s.equals("false")) return false;

        return defaultValue;
    }
}
