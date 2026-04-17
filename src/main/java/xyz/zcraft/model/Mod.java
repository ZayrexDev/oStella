package xyz.zcraft.model;

import lombok.Data;

import java.util.Map;

@Data
public class Mod {
    private String acronym;
    private Map<String, Object> settings;

    public String getColorHex() {
        return switch (acronym) {
            case "EZ", "NF", "HT", "DC" -> "#b2ff66";
            case "HR", "SD", "PF", "DT", "NC", "HD", "TC", "FL", "BL", "ST", "AC" -> "#ff6666";
            case "AT", "CN", "RX", "AP", "SO" -> "#66ccff";
            case "TP", "DA", "CL", "RD", "MR", "AL", "SG" -> "#8c66ff";
            case "TR", "WG", "SI", "GR", "DF", "WU", "WD", "BR", "AD", "MU", "NS", "MG", "RP", "AS", "FR", "BU", "SY",
                 "DP", "BM" -> "#ff66ab";
            default -> "#ffd966";
        };
    }

    public String getTextColorHex() {
        return switch (acronym) {
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
