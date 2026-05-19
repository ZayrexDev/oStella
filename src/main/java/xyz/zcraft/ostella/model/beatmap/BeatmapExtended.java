package xyz.zcraft.ostella.model.beatmap;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class BeatmapExtended extends Beatmap {
    public Double accuracy;

    public Double ar;

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

    @SerializedName("max_combo")
    public Integer maxCombo;

    public Integer ranked;

    public String url;

    public Beatmapset beatmapset;

    public FailTimes failtimes;

    public List<Owner> owners;

    @Data
    public static class Owner {
        public Integer id;
        public String username;
    }

    @Data
    public static class FailTimes {
        public List<Integer> fail;
        public List<Integer> exit;
    }
}
