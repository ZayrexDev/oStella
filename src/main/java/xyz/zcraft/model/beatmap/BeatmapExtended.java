package xyz.zcraft.model.beatmap;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public String getOwnersString() {
        return owners.stream()
                .map(owner -> owner.username)
                .collect(Collectors.joining(", "));
    }

    public double getPassRate() {
        if (passcount == null || playcount == null) return 0;
        if (playcount == 0) return 0;
        return ((double) passcount / playcount) * 100.0;
    }

    public String getODString() {
        if (accuracy == null) return "N/A";
        return "±" + String.format("%.1f", (80 - 6 * accuracy)) + "ms";
    }

    public String getARString() {
        if (ar == null) return "N/A";
        if (ar < 5) {
            return (int) (1200 + 120 * (5 - ar)) + "ms";
        } else if (ar == 5) {
            return "1200ms";
        } else {
            return (int) (1200 - 150 * (ar - 5)) + "ms";
        }
    }

    public String getCSString() {
        if (cs == null) return "N/A";
        return (int) (54.4 - 4.48 * cs) + "px";
    }

    public String getTagString() {
        return Optional.ofNullable(beatmapset)
                .map(Beatmapset::getTags)
                .map(t -> t.substring(0, Math.min(t.length(), 80)))
                .orElse("");
    }

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
