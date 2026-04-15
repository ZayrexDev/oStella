package xyz.zcraft.model.beatmap;

import lombok.Data;

import java.io.Serializable;

@Data
public final class DiffSpec implements Serializable {
    private double ppSS;
    private double ppFC;
    private double pp95;
    private double aim;
    private double speed;
}
