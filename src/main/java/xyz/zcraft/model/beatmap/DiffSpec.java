package xyz.zcraft.model.beatmap;

import lombok.Data;

import java.io.Serializable;

@Data
public final class DiffSpec implements Serializable {
    private static final double[] STAR_THRESHOLDS = {0.1, 1.25, 2.0, 2.5, 3.3, 4.2, 4.9, 5.8, 6.7, 7.7, 9.0};
    private static final String[] STAR_COLORS = {
            "#4290fb", "#4fc0ff", "#4fffd5", "#7cff4f", "#f6f05c",
            "#ff8068", "#ff4e6f", "#c645b8", "#6563de", "#18158e", "#000000"
    };
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

    public String getDiffColor() {
        if (star < 0.1) return "#aaaaaa";
        if (star >= 9.0) return "#000000";

        for (int i = 0; i < STAR_THRESHOLDS.length - 1; i++) {
            if (star >= STAR_THRESHOLDS[i] && star < STAR_THRESHOLDS[i + 1]) {
                double range = STAR_THRESHOLDS[i + 1] - STAR_THRESHOLDS[i];
                double ratio = (star - STAR_THRESHOLDS[i]) / range;

                return interpolateHex(STAR_COLORS[i], STAR_COLORS[i + 1], ratio);
            }
        }
        return "#000000";
    }

    public String getDiffTextColor() {
        return star < 6.5 ? "#000000" : "#ffffff";
    }

    private String interpolateHex(String hex1, String hex2, double ratio) {
        int r1 = Integer.valueOf(hex1.substring(1, 3), 16);
        int g1 = Integer.valueOf(hex1.substring(3, 5), 16);
        int b1 = Integer.valueOf(hex1.substring(5, 7), 16);

        int r2 = Integer.valueOf(hex2.substring(1, 3), 16);
        int g2 = Integer.valueOf(hex2.substring(3, 5), 16);
        int b2 = Integer.valueOf(hex2.substring(5, 7), 16);

        int r = (int) Math.round(r1 + (r2 - r1) * ratio);
        int g = (int) Math.round(g1 + (g2 - g1) * ratio);
        int b = (int) Math.round(b1 + (b2 - b1) * ratio);

        return String.format("#%02x%02x%02x", r, g, b);
    }

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

    public String getIntDiffChangeStr(double diff) {
        if (diff < 0.1 && diff > -0.1) return "~0";
        else if (diff > 0) return "▲" + (int) diff;
        else return "▼" + (int) diff;
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
