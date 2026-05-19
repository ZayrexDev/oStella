package xyz.zcraft.ostella.util;

public class Colors {
    private static final double[] STAR_THRESHOLDS = {0.1, 1.25, 2.0, 2.5, 3.3, 4.2, 4.9, 5.8, 6.7, 7.7, 9.0};
    private static final String[] STAR_COLORS = {
            "#4290fb", "#4fc0ff", "#4fffd5", "#7cff4f", "#f6f05c",
            "#ff8068", "#ff4e6f", "#c645b8", "#6563de", "#18158e", "#000000"
    };

    public static String getBeatmapDiffColor(double star) {
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

    public static String getBeatmapDiffTextColor(double star) {
        return star < 6.5 ? "#000000" : "#ffffff";
    }

    public static String getBeatmapStatusColor(String status) {
        return switch (status.toUpperCase()) {
            case "RANKED", "APPROVED" -> "#b3ff66";
            case "LOVED" -> "#ff66ab";
            case "QUALIFIED" -> "#4fc0ff";
            case "PENDING" -> "#ffd966";
            case "WIP" -> "#ff9966";
            case "GRAVEYARD" -> "#000000";
            default -> "#ffffff";
        };
    }

    public static String getBeatmapStatusTextColor(String status) {
        return switch (status.toUpperCase()) {
            case "RANKED", "LOVED", "PENDING", "WIP", "APPROVED", "QUALIFIED" -> "#394246";
            case "GRAVEYARD" -> "#5c6970";
            default -> "#ffffff";
        };
    }

    public static String getScoreRankColor(String rank) {
        return switch (rank) {
            case "X", "XH" -> "#de31ae";
            case "SH", "S" -> "#00a8b5";
            case "A" -> "#88da20";
            case "B" -> "#ebbd48";
            case "C" -> "#ff8e5d";
            case "D" -> "#ff5a5a";
            case "F" -> "#fe004f";
            default -> "#FFFFFF";
        };
    }

    private static String interpolateHex(String hex1, String hex2, double ratio) {
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
}
