package xyz.zcraft.model.beatmap;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Covers {
    public String cover;

    @SerializedName("cover@2x")
    public String cover2x;

    public String card;

    @SerializedName("card@2x")
    public String card2x;

    public String list;

    @SerializedName("list@2x")
    public String list2x;

    public String slimcover;

    @SerializedName("slimcover@2x")
    public String slimcover2x;
}
