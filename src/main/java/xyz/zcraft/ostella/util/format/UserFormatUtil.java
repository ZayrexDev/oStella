package xyz.zcraft.ostella.util.format;

import xyz.zcraft.ostella.model.score.ScoreChange;
import xyz.zcraft.ostella.model.user.Statistics;
import xyz.zcraft.ostella.model.user.StatisticsRuleset;
import xyz.zcraft.ostella.model.user.User;
import xyz.zcraft.ostella.model.user.UserExtended;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UserFormatUtil {
    public static String getFormattedJoinDate(UserExtended user) {
        if (user == null || user.getJoinDate() == null || user.getJoinDate().isEmpty()) {
            return "Unknown";
        }

        try {
            OffsetDateTime parsedDate = OffsetDateTime.parse(user.getJoinDate());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
            return parsedDate.format(formatter);
        } catch (Exception e) {
            return user.getJoinDate();
        }
    }

    public static ScoreChange getScoreChange(UserExtended user) {
        final ScoreChange scoreChange = new ScoreChange();
        final List<Long> data = Optional.ofNullable(user)
                .map(UserExtended::getRankHistory)
                .map(UserExtended.RankHistory::getData)
                .map(List::reversed)
                .orElse(List.of());

        if (data.size() < 2) {
            return scoreChange;
        }
        scoreChange.hasData[3] = true;
        scoreChange.data[3] = Math.toIntExact(data.get(1) - data.getFirst());

        if (data.size() < 7) {
            return scoreChange;
        }
        scoreChange.hasData[2] = true;
        scoreChange.data[2] = Math.toIntExact(data.get(6) - data.getFirst());

        if (data.size() < 30) {
            return scoreChange;
        }
        scoreChange.hasData[1] = true;
        scoreChange.data[1] = Math.toIntExact(data.get(29) - data.getFirst());

        if (data.size() < 90) {
            return scoreChange;
        }
        scoreChange.hasData[0] = true;
        scoreChange.data[0] = Math.toIntExact(data.get(89) - data.getFirst());

        return scoreChange;
    }

    public static String getRankStr(User user) {
        return Optional.ofNullable(user)
                .map(User::getStatisticsRulesets)
                .map(StatisticsRuleset::getOsu)
                .map(Statistics::getGlobalRank)
                .map(l -> String.format("#%,d", l))
                .orElse("");
    }

    public static boolean havePp(User user) {
        return Optional.ofNullable(user)
                .map(User::getStatisticsRulesets)
                .map(StatisticsRuleset::getOsu)
                .map(Statistics::getPp)
                .isPresent();
    }
}

