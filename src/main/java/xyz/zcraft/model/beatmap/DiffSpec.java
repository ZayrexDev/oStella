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

    private double od;
    private double cs;
    private double ar;
    private double hp;

    private double star;
    private double bpm;

    private String modStr;
    private boolean modded = false;

    private double length;
    private double totalLength;

    public String getLengthStr() {
        return String.format("%01d", (int) (length / 60)) + ":" + String.format("%02d", (int) (length % 60));
    }

    public String getTotalLengthStr() {
        return String.format("%01d", (int) (totalLength / 60)) + ":" + String.format("%02d", (int) (totalLength % 60));
    }

    public String getDiffChangeStr(double diff) {
        if (diff < 0.1 && diff > -0.1) return "~0.00";
        else if (diff > 0) return "▲" + "%.2f".formatted(diff);
        else return "▼" + "%.2f".formatted(-diff);
    }

    public String getDiffChangeClass(double diff) {
        if (diff < 0.1 && diff > -0.1) return "spec-diff-same";
        else if (diff > 0) return "spec-diff-up";
        else return "spec-diff-down";
    }

    public String getODString() {
        return "±" + String.format("%.2f", (80 - 6 * od)) + "ms";
    }

    public String getARString() {
        if (ar < 5) {
            return (int) (1200 + 120 * (5 - ar)) + "ms";
        } else if (ar == 5) {
            return "1200ms";
        } else {
            return (int) (1200 - 150 * (ar - 5)) + "ms";
        }
    }

    public String getCSString() {
        return (int) (54.4 - 4.48 * cs) + "px";
    }
}
