package xyz.zcraft.service;

import io.github.nanamochi.rosu_pp_jar.Beatmap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.network.NetworkHelper;

import java.io.IOException;
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

    public Beatmap getRosuBeatmap(String id, boolean update) {
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
            return Beatmap.fromBytes(Files.readAllBytes(BEATMAP_CACHE.resolve(id)));
        } catch (Exception e) {
            LOG.error("Failed to load beatmap from cache!", e);
            throw new RuntimeException("Failed to load beatmap from cache!", e);
        }
    }

    private void cacheBeatmap(String id) throws Exception {
        Files.deleteIfExists(BEATMAP_CACHE.resolve(id));
        Files.writeString(BEATMAP_CACHE.resolve(id), NetworkHelper.getBeatmapString(id));
    }

    public boolean isBeatmapCached(String id) {
        return Files.exists(BEATMAP_CACHE.resolve(id));
    }
}
