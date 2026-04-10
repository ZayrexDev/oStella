package xyz.zcraft.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class BeatmapExtended {
    public Double accuracy;

    public Double ar;

    @SerializedName("beatmapset_id")
    public Long beatmapsetId;

    public Long id;

    @SerializedName("difficulty_rating")
    public Double difficultyRating;

    public String status;

    @SerializedName("total_length")
    public Long totalLength;

    @SerializedName("user_id")
    public Long userId;

    public String version;

    public Double bpm;

    public Boolean convert;

    @SerializedName("count_circles")
    public Integer countCircles;

    @SerializedName("count_sliders")
    public Integer countSliders;

    @SerializedName("count_spinners")
    public Integer countSpinners;

    public Double cs;

    @SerializedName("deleted_at")
    public String deletedAt;

    public Double drain;

    @SerializedName("hit_length")
    public Long hitLength;

    @SerializedName("is_scoreable")
    public Boolean isScoreable;

    @SerializedName("last_updated")
    public String lastUpdated;

    @SerializedName("mode_int")
    public Integer modeInt;

    @SerializedName("passcount")
    public Long passcount;

    @SerializedName("playcount")
    public Long playcount;

    public Integer ranked;

    public String url;

}
