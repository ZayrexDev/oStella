package xyz.zcraft.ostella.util.format;

import xyz.zcraft.ostella.util.Colors;
import xyz.zcraft.ostella.util.MiscUtil;
import xyz.zcraft.osu.model.*;

import java.util.ArrayList;
import java.util.List;

public class ScoreFormatUtil {
    public static String getRelativeTime(Score score) {
        if (score == null || score.getCreatedAt() == null) {
            return "";
        }
        return MiscUtil.getRelativeTimeAgo(score.getCreatedAt());
    }

    public static List<Mod> getModsList(Score score) {
        if (score == null || score.getMods() == null) {
            return new ArrayList<>();
        }
        return score.getMods().stream().map(s -> {
            final Mod mod = new Mod();
            mod.setAcronym(s);
            return mod;
        }).toList();
    }

    public static String getWeightPP(Score score) {
        if (score == null || score.getWeight() == null
                || score.getWeight().getPercentage() == null
                || score.getWeight().getPp() == null) {
            return "";
        }
        return String.format("%d%% ->%.1fpp", score.getWeight().getPercentage().intValue(), score.getWeight().getPp());
    }

    public static String getRankColor(Score score) {
        return score == null ? "#d0d0d0" : Colors.getScoreRankColor(score.getRank());
    }

    public static String getModString(Score score) {
        if (score == null || score.getMods() == null || score.getMods().isEmpty()) {
            return "[NM]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (String mod : score.getMods()) {
            sb.append(mod);
        }
        sb.append("]");

        return sb.toString();
    }
}



