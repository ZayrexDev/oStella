package xyz.zcraft.model.user;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Statistics {
    public UserExtended.Level level;
    public Double pp;

    @SerializedName("global_rank")
    public Long globalRank;

    @SerializedName("ranked_score")
    public Long rankedScore;

    @SerializedName("hit_accuracy")
    public Double hitAccuracy;

    public Double accuracy;

    @SerializedName("play_count")
    public Long playCount;

    @SerializedName("play_time")
    public Long playTime;

    @SerializedName("total_score")
    public Long totalScore;

    @SerializedName("total_hits")
    public Long totalHits;

    @SerializedName("maximum_combo")
    public Long maximumCombo;

    @SerializedName("replays_watched_by_others")
    public Long replaysWatchedByOthers;

    @SerializedName("is_ranked")
    public Boolean isRanked;

    @SerializedName("grade_counts")
    public UserExtended.GradeCounts gradeCounts;

    public UserExtended.Rank rank;
}
