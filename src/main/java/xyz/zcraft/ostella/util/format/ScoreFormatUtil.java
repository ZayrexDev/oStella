package xyz.zcraft.ostella.util.format;

import xyz.zcraft.ostella.util.Colors;
import xyz.zcraft.ostella.util.MiscUtil;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.model.Score;

public class ScoreFormatUtil {
    public static String getRelativeTime(Score score) {
        if (score == null || score.getEndedAt() == null) {
            return "";
        }
        return MiscUtil.getRelativeTimeAgo(score.getEndedAt());
    }

    public static String getWeightPP(Score score) {
        if (!hasPp(score)) {
            return "Unranked";
        }
        if (!hasWeight(score)) {
            return "";
        }
        return String.format("%d%% ↪%.1fpp", score.getWeight().getPercentage().intValue(), score.getWeight().getPp());
    }

    public static boolean hasPp(Score score) {
        return score != null && score.getPp() != null
                && "RANKED".equalsIgnoreCase(score.getBeatmap().getStatus());
    }

    public static boolean hasWeight(Score score) {
        return score != null && score.getWeight() != null
                && score.getWeight().getPercentage() != null
                && score.getWeight().getPp() != null;
    }

    public static String getRankColor(Score score) {
        return score == null ? "#d0d0d0" : Colors.getScoreRankColor(score.getRank());
    }

    public static String getModString(Score score) {
        if (score == null || score.getMods() == null || score.getMods().isEmpty()) {
            return "[NM]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (Mod mod : score.getMods()) {
            sb.append(mod.getAcronym());
        }
        sb.append("]");

        return sb.toString();
    }
}



