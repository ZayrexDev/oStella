package xyz.zcraft.ostella.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import desu.life.RosuFFI;
import xyz.zcraft.ostella.model.Mod;
import xyz.zcraft.ostella.model.beatmap.BeatmapExtended;
import xyz.zcraft.ostella.model.beatmap.DiffSpec;
import xyz.zcraft.ostella.model.score.Score;
import xyz.zcraft.ostella.network.ApiException;
import xyz.zcraft.ostella.network.ErrorCode;
import xyz.zcraft.ostella.util.format.ScoreFormatUtil;

import java.util.LinkedList;

public class BeatmapUtil {
    private static final Gson GSON = new Gson();

    public static DiffSpec getDiffSpecForMap(BeatmapExtended beatmap, String rosuPath, String mod) {
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(rosuPath);
             final RosuFFI.Performance perf = new RosuFFI.Performance()
        ) {
            final DiffSpec diffSpec = new DiffSpec();

            final RosuFFI.Mods mods = RosuFFI.Mods.fromAcronyms(mod == null ? "" : mod, RosuFFI.Mode.Osu);

            mods.removeUnknownMods();
            mods.sanitize();

            perf.setMods(mods);

            perf.setAccuracy(98.0);
            perf.setMisses(0);

            var calc = perf.calculate(rosuBeatmap);
            diffSpec.setPpFC(calc.osu.t.pp);

            perf.setAccuracy(95.0);

            calc = perf.calculate(rosuBeatmap);
            diffSpec.setPp95(calc.osu.t.pp);


            perf.setAccuracy(100.0);
            perf.setMisses(0);

            calc = perf.calculate(rosuBeatmap);
            diffSpec.setPpSS(calc.osu.t.pp);

            final RosuFFI.RosuPPLib.ScoreState scoreState = perf.generateState(rosuBeatmap);

            final var attr = calc.osu.t.difficulty;
            diffSpec.setAim(attr.aim);
            diffSpec.setSpeed(attr.speed);

            diffSpec.setBpm(beatmap.getBpm());
            diffSpec.setLength(beatmap.getHitLength());
            diffSpec.setTotalLength(beatmap.getTotalLength());
            diffSpec.setStar(calc.osu.t.difficulty.stars);

            if (mod != null && !mod.isEmpty()) {
                diffSpec.setModStr(mod);
                diffSpec.setModded(true);
            }

            diffSpec.setAr(calc.osu.t.difficulty.ar);
            diffSpec.setHp(calc.osu.t.difficulty.hp);
            diffSpec.setCs(beatmap.cs);
            diffSpec.setOd(beatmap.accuracy);

            // Calculate BPM CS Length

            if (mods.contains("HR")) {
                diffSpec.setCs(Math.min(diffSpec.getCs() * 1.3, 10));
            } else if (mods.contains("EZ")) {
                diffSpec.setCs(Math.min(diffSpec.getCs() * 0.5, 10));
            }

            if (mods.contains("DT") || mods.contains("NC")) {
                diffSpec.setBpm(diffSpec.getBpm() * 1.5);
                diffSpec.setLength(diffSpec.getLength() / 1.5);
                diffSpec.setTotalLength(diffSpec.getTotalLength() / 1.5);
            } else if (mods.contains("HT") || mods.contains("DC")) {
                diffSpec.setBpm(diffSpec.getBpm() * 0.75);
                diffSpec.setLength(diffSpec.getLength() / 0.75);
                diffSpec.setTotalLength(diffSpec.getTotalLength() / 0.75);
            }

            // Calculate OD

            boolean changingOd = false;
            double od = beatmap.getAccuracy();
            if (mods.contains("HR")) {
                changingOd = true;
                od = Math.min(od * 1.4, 10);
            } else if (mods.contains("EZ")) {
                changingOd = true;
                od = od * 0.5;
            }

            double window = 80.0 - (6.0 * od);

            if (mods.contains("DT") || mods.contains("NC")) {
                changingOd = true;
                window = window / 1.5;
            } else if (mods.contains("HT") || mods.contains("DC")) {
                changingOd = true;
                window = window / 0.75;
            }

            od = (80.0 - window) / 6;

            if (changingOd) {
                diffSpec.setOd(od);
            }

            final LinkedList<Mod> modList = new LinkedList<>();

            for (JsonElement jsonElement : JsonParser.parseString(mods.toJson().toString()).getAsJsonArray().asList()) {
                modList.add(GSON.fromJson(jsonElement, Mod.class));
            }

            diffSpec.setMods(modList);
            diffSpec.setMaxCombo(scoreState.max_combo);

            return diffSpec;
        } catch (RosuFFI.FFIException e) {
            throw new ApiException(ErrorCode.ROSU_ERROR, "Failed to calculate difficulty with RosuFFI: " + e.getMessage(), e);
        }
    }

    public static double estimatePp(Score score, String rosuPath) {
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(rosuPath);
             final RosuFFI.Performance perf = new RosuFFI.Performance()
        ) {
            perf.setMods(RosuFFI.Mods.fromAcronyms(ScoreFormatUtil.getModsList(score).stream().map(Mod::getAcronym).reduce("", String::concat), RosuFFI.Mode.Osu));

            perf.setAccuracy(score.getAccuracy() * 100);
            perf.setN300(score.getStatistics().get("count_300"));
            perf.setN100(score.getStatistics().get("count_100"));
            perf.setN50(score.getStatistics().get("count_50"));
            perf.setMisses(score.getStatistics().get("count_miss"));

            var calc = perf.calculate(rosuBeatmap);

            return calc.osu.t.pp;
        } catch (RosuFFI.FFIException e) {
            throw new ApiException(ErrorCode.ROSU_ERROR, "Failed to estimate pp with RosuFFI: " + e.getMessage(), e);
        }
    }
}
