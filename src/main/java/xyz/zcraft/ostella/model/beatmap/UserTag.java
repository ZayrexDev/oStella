package xyz.zcraft.ostella.model.beatmap;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UserTag {
    public int id;
    public String name;
    @SerializedName("ruleset_id")
    public int rulesetId;
    public String description;
}
