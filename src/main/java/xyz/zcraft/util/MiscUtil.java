package xyz.zcraft.util;

import java.time.Duration;
import java.time.Instant;

public class MiscUtil {
    public static boolean isInteger(String... str) {
        for (String s : str) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean isDouble(String... str) {
        for (String s : str) {
            try {
                Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean notNumber(String... str) {
        for (String s : str) {
            if (s == null) return true;
            try {
                Long.parseLong(s);
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }

    public static String getRelativeTimeAgo(String isoTimestamp) {
        Instant pastTime = Instant.parse(isoTimestamp);
        Instant now = Instant.now();

        Duration duration = Duration.between(pastTime, now);

        long days = duration.toDays();
        if (days > 0) {
            return days + (days == 1 ? " dy ago" : " dys ago");
        }

        long hours = duration.toHours();
        if (hours > 0) {
            return hours + (hours == 1 ? " hr ago" : " hrs ago");
        }

        long minutes = duration.toMinutes();
        if (minutes > 0) {
            return minutes + (minutes == 1 ? " min ago" : " mins ago");
        }

        long seconds = duration.getSeconds();
        if (seconds < 5) {
            return "just now";
        }
        return seconds + " secs ago";
    }
}
