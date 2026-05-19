package xyz.zcraft.ostella.model.score;

import lombok.Data;
import xyz.zcraft.ostella.model.user.UserExtended;

@Data
public class Placement {
    public Score score;
    public UserExtended user;
}
