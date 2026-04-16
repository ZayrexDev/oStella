package xyz.zcraft.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.network.OsuAPI;

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

    /// @deprecated Use {@link #getRosuBeatmapPath(String, boolean)} instead to avoid loading the entire beatmap into memory.
    /// Loading beatmaps from byte[] may not work on Linux systems.
    @Deprecated
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
            return Files.readAllBytes(BEATMAP_CACHE.resolve(id));
        } catch (Exception e) {
            LOG.error("Failed to load beatmap from cache!", e);
            throw new RuntimeException("Failed to load beatmap from cache!", e);
        }
    }

    public String getRosuBeatmapPath(String id, boolean update) {
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

    public boolean isBeatmapCached(String id) {
        return Files.exists(BEATMAP_CACHE.resolve(id));
    }
}
