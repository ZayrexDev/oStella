package xyz.zcraft.service;

import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.model.TokenData;
import xyz.zcraft.network.OsuAPI;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.stream.Stream;

public class CacheService {
    private static final Logger LOG = LogManager.getLogger(CacheService.class);
    private static final Path BEATMAP_CACHE = Paths.get("data", "cache", "beatmap");
    private static final Path IMAGE_CACHE = Paths.get("data", "cache", "image");
    private static final Path REPLAY_CACHE = Paths.get("data", "cache", "replay");
    private static final Path DANSER_SONG_CACHE = Paths.get("data", "cache", "danser", "songs");

    private final AsyncService executor;

    public CacheService(AsyncService executor) throws IOException {
        this.executor = executor;
        Files.createDirectories(BEATMAP_CACHE);
        Files.createDirectories(IMAGE_CACHE);
        Files.createDirectories(REPLAY_CACHE);
        Files.createDirectories(DANSER_SONG_CACHE);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String extractExtension(String url) {
        // Need to deal with these kinds of URLs...
        // https://assets.ppy.sh/beatmaps/320118/covers/cover.jpg?1650632079
        // https://assets.ppy.sh/user-profile-covers/21445688/2934e792b3acd2f1a5d75caa8f1ee1fa06a52e2c6f4c0bd4457368db4770dc80.png
        // https://a.ppy.sh/21445688?1754893780.png

        if (url == null || url.isEmpty()) {
            return "png";
        }

        int lastDotIndex = url.lastIndexOf('.');
        int lastSlashIndex = url.lastIndexOf('/');

        if (lastDotIndex > lastSlashIndex) {

            String extension = url.substring(lastDotIndex + 1);

            int questionMarkIndex = extension.indexOf('?');
            if (questionMarkIndex != -1) {
                extension = extension.substring(0, questionMarkIndex);
            }

            return extension;
        }

        return "png";
    }

    public String getRosuBeatmapPath(String id, boolean update) {
        if (!Files.exists(BEATMAP_CACHE.resolve(id)) || update) {
            try {
                LOG.debug("Caching beatmap {}", id);
                cacheBeatmap(id);
                LOG.debug("Beatmap {} cached", id);
            } catch (Exception e) {
                LOG.error("Failed to download beatmap!", e);
                throw new RuntimeException("Failed to download beatmap!", e);
            }
        }

        try {
            return BEATMAP_CACHE.resolve(id).toAbsolutePath().toString();
        } catch (Exception e) {
            LOG.error("Failed to load beatmap from cache!", e);
            throw new RuntimeException("Failed to load beatmap from cache!", e);
        }
    }

    private void cacheBeatmap(String id) throws Exception {
        Files.deleteIfExists(BEATMAP_CACHE.resolve(id));
        Files.write(BEATMAP_CACHE.resolve(id), executor.enqueueAsync(() -> OsuAPI.getBeatmapBytes(id)).join());
    }

    @NotNull
    @SneakyThrows(NoSuchAlgorithmException.class)
    private String getFileName(String url) {
        String extension = extractExtension(url);

        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(url.getBytes(StandardCharsets.UTF_8));

        return bytesToHex(digest) + "." + extension;
    }

    public String getImageSrc(String url) {
        final String fileName = getFileName(url);

        if (!Files.exists(IMAGE_CACHE.resolve(fileName))) {
            try {
                cacheImage(fileName, url);
                LOG.debug("Image {} cached", fileName);
            } catch (Exception e) {
                LOG.error("Failed to download image!", e);
                throw new RuntimeException("Failed to download image!", e);
            }
        }

        try {
            return "http://ostella-cache/"
                    + IMAGE_CACHE.resolve(getFileName(url)).toAbsolutePath().toString().replace("\\", "/");
        } catch (Exception e) {
            LOG.error("Failed to load image from cache!", e);
            throw new RuntimeException("Failed to load image from cache!", e);
        }
    }

    private void cacheImage(String fileName, String url) throws Exception {
        Files.deleteIfExists(IMAGE_CACHE.resolve(fileName));
        Files.write(IMAGE_CACHE.resolve(fileName), Objects.requireNonNull(OsuAPI.getImageBytes(url)));
    }

    public Path getImagePathFromFilename(String filename) {
        return IMAGE_CACHE.resolve(filename);
    }

    public boolean cacheBeatmapset(String id) {
        try (Stream<Path> list = Files.list(DANSER_SONG_CACHE)) {
            if (list.map(Path::getFileName)
                    .map(Path::toString)
                    .anyMatch(p -> p.equals(id) || p.startsWith(id + " ") || p.equals(id + ".osz"))
            ) {
                LOG.debug("Beatmapset {} is already cached, skipping", id);
                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path beatmapsetPath = DANSER_SONG_CACHE.resolve(id + ".osz");

        LOG.debug("Downloading beatmapset {} via Sayobot", id);
        if (!downloadSayobot(id, beatmapsetPath)) {
            LOG.warn("Switching to Nekoha");
            if (!downloadNekoha(id, beatmapsetPath)) {
                LOG.error("Failed to download beatmapset {} via both Sayobot and Nekoha!", id);
                return false;
            }
        }

        return true;
    }

    private boolean downloadNekoha(String id, Path beatmapsetPath) {
        try (final HttpClient client = HttpClient.newBuilder().build()) {
            String initialUrl = "https://mirror.nekoha.moe/api4/download/" + id;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(initialUrl))
                    .GET()
                    .build();

            HttpResponse<InputStream> fileResponse = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (fileResponse.statusCode() == 200) {
                Files.copy(fileResponse.body(), beatmapsetPath, StandardCopyOption.REPLACE_EXISTING);
                LOG.debug("Beatmapset {} cached via Nekoha", beatmapsetPath);
                return true;
            } else {
                LOG.error("Failed to download beatmapset! Nekoha responded with status code: {}", fileResponse.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to download beatmapset!", e);
        }
        return false;
    }

    private boolean downloadSayobot(String id, Path beatmapsetPath) {
        try (final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            String initialUrl = "https://dl.sayobot.cn/beatmaps/download/novideo/" + id;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(initialUrl))
                    .GET()
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() == 301 || response.statusCode() == 302) {
                String dirtyLocation = response.headers().firstValue("Location")
                        .orElseThrow(() -> new RuntimeException("Sayobot redirected without a Location header"));

                String cleanLocation = dirtyLocation
                        .replace(" ", "%20")
                        .replace("[", "%5B")
                        .replace("]", "%5D")
                        .replace("^", "%5E");

                HttpRequest actualDownloadRequest = HttpRequest.newBuilder()
                        .uri(URI.create(cleanLocation))
                        .GET()
                        .build();

                HttpResponse<InputStream> fileResponse = client.send(actualDownloadRequest, HttpResponse.BodyHandlers.ofInputStream());

                if (fileResponse.statusCode() == 200) {
                    Files.copy(fileResponse.body(), beatmapsetPath, StandardCopyOption.REPLACE_EXISTING);
                    LOG.debug("Beatmapset {} cached via Sayobot", beatmapsetPath);
                    return true;
                } else {
                    LOG.error("Failed to download beatmapset! Sayobot responded with status code: {}", fileResponse.statusCode());
                }
            }
        } catch (IOException | InterruptedException e) {
            LOG.error("Failed to download beatmapset!", e);
        }
        return false;
    }

    public Path getReplay(TokenData tokenData, String id) throws Exception {
        Path beatmapsetPath = REPLAY_CACHE.resolve(id + ".osr");

        if (!Files.exists(beatmapsetPath)) {
            LOG.debug("Caching replay {}", id);
            Files.write(beatmapsetPath, executor.enqueueAsync(() -> OsuAPI.getReplayBytes(tokenData, id)).join());
        }

        LOG.debug("Replay {} is ready", id);

        return beatmapsetPath;
    }

    public Path getDanserCache() {
        return DANSER_SONG_CACHE;
    }
}
