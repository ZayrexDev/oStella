package xyz.zcraft.data;

import lombok.Data;

@Data
public class ScoreChange {
    // last 3m 1m 1w 1d
    public boolean[] hasData = new boolean[]{false, false, false, false};
    public int[] data = new int[]{0, 0, 0, 0};

    public String getSubfix(int i) {
        if (!hasData[i]) {
            return null;
        }

        if (data[i] > 0) {
            return "up";
        } else if (data[i] < 0) {
            return "down";
        } else {
            return "same";
        }
    }

    public String getText(int i) {
        if (!hasData[i]) {
            return null;
        }

        if (data[i] > 0) {
            return "▲ " + String.format("%,d", data[i]);
        } else if (data[i] < 0) {
            return "▼ " + String.format("%,d", -data[i]);
        } else {
            return "~ 0";
        }
    }
}
