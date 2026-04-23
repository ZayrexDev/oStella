package xyz.zcraft.model.beatmap;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import xyz.zcraft.util.Colors;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
public class Beatmap {
    @SerializedName("beatmapset_id")
    public Long beatmapsetId;
    public Long id;
    @SerializedName("difficulty_rating")
    public Double difficultyRating;
    public Object mode;
    public String status;
    @SerializedName("total_length")
    public Long totalLength;
    @SerializedName("user_id")
    public Long userId;
    public String version;
    @SerializedName("top_tag_ids")
    public List<UserTagId> topUserTagIds;

    public String getDiffColor() {
        return Colors.getBeatmapDiffColor(difficultyRating);
    }

    public String getDiffTextColor() {
        return Colors.getBeatmapDiffTextColor(difficultyRating);
    }

    public String getStatusColor() {
        return Colors.getBeatmapStatusColor(status);
    }

    public String getStatusTextColor() {
        return Colors.getBeatmapStatusTextColor(status);
    }

    public boolean hasLeaderboard() {
        return Optional.ofNullable(getStatus())
                .map(String::toUpperCase)
                .map(s -> Objects.equals(s, "RANKED") || Objects.equals(s, "LOVED"))
                .orElse(false);
    }
}
