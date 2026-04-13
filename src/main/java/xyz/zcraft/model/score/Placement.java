package xyz.zcraft.model.score;

import lombok.Data;
import xyz.zcraft.model.user.UserExtended;

@Data
public class Placement {
    public Score score;
    public UserExtended user;
}
