package xyz.zcraft.model.beatmap;

public record SearchResultItem(
        long beatmapsetId,
        String artist,
        String title,
        String mapperName,
        double minStar,
        double maxStar
) {
    public static SearchResultItem fromBeatmapset(Beatmapset ms) {
        return new SearchResultItem(ms.getId(), ms.getTitle(), ms.getArtist(), ms.getCreator(), ms.getMinStar(), ms.getMaxStar());
    }
}
