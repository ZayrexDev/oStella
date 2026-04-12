package xyz.zcraft.model.score;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.Beatmapset;

import java.util.HashMap;
import java.util.List;

@Data
public class Score {
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
            case "F" -> "#fe004f";
            default -> "#FFFFFF";
        };
    }

    public static class ScoreStatistics extends HashMap<String, Long> {
    }
}
