package xyz.zcraft.ostella.data;

import xyz.zcraft.ostella.util.format.BeatmapsetFormatUtil;
import xyz.zcraft.osu.model.*;

public record SearchResultItem(
        long beatmapsetId,
        String artist,
        String title,
        String mapperName,
        double minStar,
        double maxStar,
        String coverUrl
) {
    public static SearchResultItem fromBeatmapset(Beatmapset ms) {
        return new SearchResultItem(
                ms.getId(), ms.getTitle(), ms.getArtist(), ms.getCreator(),
                BeatmapsetFormatUtil.getMinStar(ms), BeatmapsetFormatUtil.getMaxStar(ms), ms.getCovers().getSlimcover()
        );
    }
}
