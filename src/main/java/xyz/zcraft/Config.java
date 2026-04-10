package xyz.zcraft;

public record Config(String clientId, String clientSecret, String port, String path, String maxThreads, String delay) {
}
