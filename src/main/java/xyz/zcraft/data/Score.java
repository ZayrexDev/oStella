package xyz.zcraft.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.HashMap;
import java.util.List;

@Data
public class Score {
    private static final double[] STAR_THRESHOLDS = {0.1, 1.25, 2.0, 2.5, 3.3, 4.2, 4.9, 5.8, 6.7, 7.7, 9.0};
    private static final String[] STAR_COLORS = {
            "#4290fb", "#4fc0ff", "#4fffd5", "#7cff4f", "#f6f05c",
            "#ff8068", "#ff4e6f", "#c645b8", "#6563de", "#18158e", "#000000"
    };
    public Double accuracy;
    @SerializedName("beatmap_id")
    public Long beatmapId;
    @SerializedName("created_at")
    public String createdAt;
    @SerializedName("build_id")
    public Long buildId;
    @SerializedName("classic_total_score")
    public Long classicTotalScore;
    @SerializedName("ended_at")
    public String endedAt;
    @SerializedName("has_replay")
    public Boolean hasReplay;
    public Long id;
    @SerializedName("is_perfect_combo")
    public Boolean isPerfectCombo;
    @SerializedName("legacy_perfect")
    public Boolean legacyPerfect;
    @SerializedName("legacy_score_id")
    public Long legacyScoreId;
    @SerializedName("legacy_total_score")
    public Long legacyTotalScore;
    @SerializedName("max_combo")
    public Long maxCombo;
    @SerializedName("maximum_statistics")
    public ScoreStatistics maximumStatistics;
    public List<String> mods;
    public Boolean passed;
    @SerializedName("playlist_item_id")
    public Long playlistItemId;
    public Double pp;
    public Boolean preserve;
    public Boolean processed;
    public String rank;
    public Boolean ranked;
    @SerializedName("room_id")
    public Long roomId;
    @SerializedName("ruleset_id")
    public Long rulesetId;
    @SerializedName("started_at")
    public String startedAt;
    public ScoreStatistics statistics;
    public String type;
    @SerializedName("user_id")
    public Long userId;
    public Long score;
    public BeatmapExtended beatmap;
    public Beatmapset beatmapset;

    public String getRankColor() {
        return switch (rank) {
            case "X", "XH" -> "#de31ae";
            case "SH", "S" -> "#00a8b5";
            case "A" -> "#88da20";
            case "B" -> "#ebbd48";
            case "C" -> "#ff8e5d";
            case "D" -> "#ff5a5a";
            default -> "#FFFFFF";
        };
    }

    public String getDiffColor() {
        if (this.beatmap.difficultyRating < 0.1) return "#aaaaaa";
        if (this.beatmap.difficultyRating >= 9.0) return "#000000";

        for (int i = 0; i < STAR_THRESHOLDS.length - 1; i++) {
            if (this.beatmap.difficultyRating >= STAR_THRESHOLDS[i] && this.beatmap.difficultyRating < STAR_THRESHOLDS[i + 1]) {
                double range = STAR_THRESHOLDS[i + 1] - STAR_THRESHOLDS[i];
                double ratio = (this.beatmap.difficultyRating - STAR_THRESHOLDS[i]) / range;

                return interpolateHex(STAR_COLORS[i], STAR_COLORS[i + 1], ratio);
            }
        }
        return "#000000";
    }

    public String getDiffTextColor() {
        return this.beatmap.difficultyRating < 6.5 ? "#000000" : "#ffffff";
    }

    private String interpolateHex(String hex1, String hex2, double ratio) {
        int r1 = Integer.valueOf(hex1.substring(1, 3), 16);
        int g1 = Integer.valueOf(hex1.substring(3, 5), 16);
        int b1 = Integer.valueOf(hex1.substring(5, 7), 16);

        int r2 = Integer.valueOf(hex2.substring(1, 3), 16);
        int g2 = Integer.valueOf(hex2.substring(3, 5), 16);
        int b2 = Integer.valueOf(hex2.substring(5, 7), 16);

        int r = (int) Math.round(r1 + (r2 - r1) * ratio);
        int g = (int) Math.round(g1 + (g2 - g1) * ratio);
        int b = (int) Math.round(b1 + (b2 - b1) * ratio);

        return String.format("#%02x%02x%02x", r, g, b);
    }

    public static class ScoreStatistics extends HashMap<String, Long> {
    }
}
