package xyz.zcraft.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.network.OsuAPI;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BeatmapCacheService {
    private static final Logger LOG = LogManager.getLogger(BeatmapCacheService.class);
    private static final Path BEATMAP_CACHE = Paths.get("data", "cache", "beatmap");

    public BeatmapCacheService() throws IOException {
        if (!Files.exists(BEATMAP_CACHE)) {
            Files.createDirectories(BEATMAP_CACHE);
        }
    }

    public byte[] getRosuBeatmapBytes(String id, boolean update) {
        if (!isBeatmapCached(id) || update) {
            try {
                cacheBeatmap(id);
                LOG.info("Beatmap {} cached", id);
            } catch (Exception e) {
                LOG.error("Failed to download beatmap!", e);
                throw new RuntimeException("Failed to download beatmap!", e);
            }
        }

        try {
            byte[] bytes = Files.readAllBytes(BEATMAP_CACHE.resolve(id));
            System.out.println("beatmap bytes len=" + bytes.length);
            System.out.println("first 32 bytes=" + new String(bytes, 0, 32, StandardCharsets.UTF_8));
            System.out.println("last 32 bytes=" + new String(bytes, Math.max(0, bytes.length - 32), Math.min(32, bytes.length), StandardCharsets.UTF_8));


            return Files.readAllBytes(BEATMAP_CACHE.resolve(id));
        } catch (Exception e) {
            LOG.error("Failed to load beatmap from cache!", e);
            throw new RuntimeException("Failed to load beatmap from cache!", e);
        }
    }

    private void cacheBeatmap(String id) throws Exception {
        Files.deleteIfExists(BEATMAP_CACHE.resolve(id));
        Files.write(BEATMAP_CACHE.resolve(id), OsuAPI.getBeatmapBytes(id));
    }

    public boolean isBeatmapCached(String id) {
        return Files.exists(BEATMAP_CACHE.resolve(id));
    }
}
