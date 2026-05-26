package xyz.zcraft.ostella.util.format;

import xyz.zcraft.ostella.util.Colors;
import xyz.zcraft.osu.model.Mod;

public class ModFormatUtil {
    public static String getColorHex(Mod mod) {
        return Colors.getModColor(mod.getAcronym());
    }

    public static String getTextColorHex(Mod mod) {
        return Colors.getModTextColor(mod.getAcronym());
    }
}

