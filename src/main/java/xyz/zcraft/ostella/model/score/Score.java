package xyz.zcraft.ostella.model.score;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import xyz.zcraft.ostella.model.Mod;
import xyz.zcraft.ostella.model.beatmap.BeatmapExtended;
import xyz.zcraft.ostella.model.beatmap.Beatmapset;
import xyz.zcraft.ostella.model.user.User;
import xyz.zcraft.ostella.util.Colors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static xyz.zcraft.ostella.util.MiscUtil.getRelativeTimeAgo;

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
    @SerializedName("replay")
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
    @SerializedName("user")
    public User user;
    public Long score;
    public BeatmapExtended beatmap;
    public Beatmapset beatmapset;
    public Weight weight;

    public String getRelativeTime() {
        if (createdAt == null) return "";
        return getRelativeTimeAgo(createdAt);
    }

    public List<Mod> getModsList() {
        if (mods == null) return new ArrayList<>();
        return mods.stream().map(s -> new Mod(s, null)).toList();
    }

    public String getWeightPP() {
        if (weight != null && weight.percentage != null && weight.pp != null) {
            return String.format("%d%% ↪%.1fpp", weight.percentage.intValue(), weight.pp);
        } else {
            return "";
        }
    }

    public String getRankColor() {
        return Colors.getScoreRankColor(rank);
    }

    public String getModString() {
        if (mods == null || mods.isEmpty()) return "[NM]";

        StringBuilder sb = new StringBuilder("[");
        for (String mod : mods) {
            sb.append(mod);
        }
        sb.append("]");

        return sb.toString();
    }

    public static class ScoreStatistics extends HashMap<String, Long> {
    }

    @Data
    public static class Weight {
        private Double percentage;
        private Double pp;
    }
}
