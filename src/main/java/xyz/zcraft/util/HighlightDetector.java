package xyz.zcraft.util;

import desu.life.RosuFFI;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HighlightDetector {
    public static Highlight calculateHighlight(String osuFilePath) throws Exception {
        List<Double> timestamps = extractTimestamps(osuFilePath);
        if (timestamps.isEmpty()) return new Highlight(0, 10);

        double[] starRatings = new double[timestamps.size()];

        try (
                RosuFFI.Beatmap beatmap = new RosuFFI.Beatmap(osuFilePath);
                RosuFFI.Difficulty diff = new RosuFFI.Difficulty()
        ) {
            RosuFFI.GradualDifficulty gradDiff = RosuFFI.GradualDifficulty.New(diff, beatmap);

            for (int i = 0; i < timestamps.size(); i++) {
                var opt = gradDiff.Next();

                if (opt.is_some == 0) break;

                if (opt.t.mode == 0) {
                    final Optional<RosuFFI.RosuPPLib.OsuDifficultyAttributes> optional = opt.t.osu.toOptional();
                    if (optional.isPresent()) {
                        starRatings[i] = optional.get().stars;
                    } else {
                        throw new RuntimeException("Failed to get difficulty attributes");
                    }
                }
            }
        }

        final double bufferedStart = getBufferedStart(timestamps, starRatings);
        double finalEnd = bufferedStart + 11.5;

        return new Highlight(bufferedStart, finalEnd);
    }

    private static double getBufferedStart(List<Double> timestamps, double[] starRatings) {
        double maxStarSpike = 0;
        int bestStartIndex = 0;
        double windowDurationMs = 10000;

        for (int i = 0; i < timestamps.size(); i++) {
            double windowStartTime = timestamps.get(i);
            int windowEndIndex = i;

            while (windowEndIndex < timestamps.size() &&
                    timestamps.get(windowEndIndex) - windowStartTime <= windowDurationMs) {
                windowEndIndex++;
            }

            if (windowEndIndex >= timestamps.size()) {
                windowEndIndex = timestamps.size() - 1;
            }

            double starSpike = starRatings[windowEndIndex] - starRatings[i];

            if (starSpike > maxStarSpike) {
                maxStarSpike = starSpike;
                bestStartIndex = i;
            }
        }

        double highlightStart = timestamps.get(bestStartIndex) / 1000.0;

        return Math.max(0, highlightStart - 1.5);
    }

    public static List<Double> extractTimestamps(String osuFilePath) throws Exception {
        List<Double> timestamps = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(osuFilePath))) {
            String line;
            boolean inHitObjects = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().equals("[HitObjects]")) {
                    inHitObjects = true;
                    continue;
                }
                if (inHitObjects && !line.trim().isEmpty()) {
                    String[] parts = line.split(",");
                    if (parts.length >= 3) {
                        timestamps.add(Double.parseDouble(parts[2]));
                    }
                }
            }
        }
        return timestamps;
    }
}
