package xyz.zcraft.ostella.data;

import lombok.Data;
import xyz.zcraft.osu.model.Score;
import xyz.zcraft.osu.model.UserExtended;

@Data
public class Placement {
    public Score score;
    public UserExtended user;
}
