package xyz.zcraft.model.replay;

import java.util.List;

public record OsuReplay(
        byte gameMode,
        int gameVersion,
        String beatmapHash,
        String playerName,
        String replayHash,
        short count300,
        short count100,
        short count50,
        short countGeki,
        short countKatu,
        short countMiss,
        int totalScore,
        short maxCombo,
        boolean perfectCombo,
        int mods,
        String lifeBarGraph,
        long timestamp,
        List<KeyFrame> keyFrames
) {
    public record KeyFrame(
            long offset,
            float cursorX,
            float cursorY,
            int key
    ) {}
}
