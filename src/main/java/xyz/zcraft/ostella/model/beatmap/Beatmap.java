package xyz.zcraft.ostella.model.beatmap;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

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

}
