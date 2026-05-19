package xyz.zcraft.ostella.util.format;
import xyz.zcraft.osu.model.*;

public class ModFormatUtil {
    public static String getColorHex(Mod mod) {
        if (mod == null) {
            return "#ffd966";
        }

        return switch (mod.getAcronym()) {
            case "EZ", "NF", "HT", "DC" -> "#b2ff66";
            case "HR", "SD", "PF", "DT", "NC", "HD", "TC", "FL", "BL", "ST", "AC" -> "#ff6666";
            case "AT", "CN", "RX", "AP", "SO" -> "#66ccff";
            case "TP", "DA", "CL", "RD", "MR", "AL", "SG" -> "#8c66ff";
            case "TR", "WG", "SI", "GR", "DF", "WU", "WD", "BR", "AD", "MU", "NS", "MG", "RP", "AS", "FR", "BU", "SY",
                    "DP", "BM" -> "#ff66ab";
            default -> "#ffd966";
        };
    }

    public static String getTextColorHex(Mod mod) {
        if (mod == null) {
            return "#d8b856";
        }

        return switch (mod.getAcronym()) {
            case "EZ", "NF", "HT", "DC" -> "#3c591e";
            case "HR", "SD", "PF", "DT", "NC", "HD", "TC", "FL", "BL", "ST", "AC" -> "#591e1e";
            case "AT", "CN", "RX", "AP", "SO" -> "#1e4559";
            case "TP", "DA", "CL", "RD", "MR", "AL", "SG" -> "#2d1e59";
            case "TR", "WG", "SI", "GR", "DF", "WU", "WD", "BR", "AD", "MU", "NS", "MG", "RP", "AS", "FR", "BU", "SY",
                    "DP", "BM" -> "#591e39";
            default -> "#d8b856";
        };
    }
}

