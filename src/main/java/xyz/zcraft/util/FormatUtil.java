package xyz.zcraft.util;

public class FormatUtil {
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

    public static boolean isLong(String... str) {
        for (String s : str) {
            try {
                Long.parseLong(s);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
