package xyz.zcraft.service;

import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.network.OsuAPI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CacheService {
    private static final Logger LOG = LogManager.getLogger(CacheService.class);
    private static final Path BEATMAP_CACHE = Paths.get("data", "cache", "beatmap");
    private static final Path IMAGE_CACHE = Paths.get("data", "cache", "image");

    public CacheService() throws IOException {
        if (!Files.exists(BEATMAP_CACHE)) {
            Files.createDirectories(BEATMAP_CACHE);
        }
        if (!Files.exists(IMAGE_CACHE)) {
            Files.createDirectories(IMAGE_CACHE);
        }
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
                cacheBeatmap(id);
                LOG.info("Beatmap {} cached", id);
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
        Files.write(BEATMAP_CACHE.resolve(id), OsuAPI.getBeatmapBytes(id));
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
                LOG.info("Image {} cached", fileName);
            } catch (Exception e) {
                LOG.error("Failed to download image!", e);
                throw new RuntimeException("Failed to download image!", e);
            }
        }

        try {
            return "http://ostella-cache/" + IMAGE_CACHE.resolve(getFileName(url)).toAbsolutePath().toString().replace("\\", "/");

        } catch (Exception e) {
            LOG.error("Failed to load image from cache!", e);
            throw new RuntimeException("Failed to load image from cache!", e);
        }
    }

    private void cacheImage(String fileName, String url) throws Exception {
        Files.deleteIfExists(IMAGE_CACHE.resolve(fileName));
        Files.write(IMAGE_CACHE.resolve(fileName), OsuAPI.getImageBytes(url));
    }

    public Path getImagePathFromFilename(String filename) {
        return IMAGE_CACHE.resolve(filename);
    }
}
