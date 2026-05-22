package xyz.zcraft.ostella.util.format;

import java.util.Optional;
import java.util.stream.Collectors;
import xyz.zcraft.osu.model.*;

public class BeatmapFormatUtil {
    public static boolean hasLeaderboard(Beatmap beatmap) {
        return Optional.ofNullable(beatmap.getStatus())
                .map(s -> "RANKED".equalsIgnoreCase(s) || "LOVED".equalsIgnoreCase(s))
                .orElse(false);
    }

    public static String getOwnersString(BeatmapExtended beatmap) {
        return beatmap.getOwners().stream()
                .map(owner -> owner.username)
                .collect(Collectors.joining(", "));
    }

    public static double getPassRate(BeatmapExtended beatmap) {
        final Long passcount = beatmap.getPasscount();
        final Long playcount = beatmap.getPlaycount();
        if (passcount == null || playcount == null) return 0;
        if (playcount == 0) return 0;
        return ((double) passcount / playcount) * 100.0;
    }

    public static String getTagString(BeatmapExtended beatmap) {
        return Optional.ofNullable(beatmap.getBeatmapset())
                .map(Beatmapset::getTags)
                .map(t -> t.substring(0, Math.min(t.length(), 80)) + (t.length() > 80 ? "..." : ""))
                .orElse("");
    }
}
