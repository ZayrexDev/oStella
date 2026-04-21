package xyz.zcraft.model.beatmap;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UserTagId {
    @SerializedName("tag_id")
    public int tagId;
    public int count;
}
