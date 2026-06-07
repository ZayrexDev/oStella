package xyz.zcraft.ostella;

import org.junit.jupiter.api.Test;
import xyz.zcraft.ostella.service.MissVisualizeService;
import xyz.zcraft.osu.parser.BeatmapParser;
import xyz.zcraft.osu.parser.ReplayAnalyzer;
import xyz.zcraft.osu.parser.ReplayParser;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.zcraft.ostella.Util.getRes;

public class MissVisualizeTest {
    @Test
    public void missVisualizeTest() throws Exception {
        final OsuBeatmap osuBeatmap = BeatmapParser.parseBeatmap(getRes("beatmaps/468994.osu"));
        final OsuReplay osuReplay = ReplayParser.parseReplay(getRes("replays/solo-replay-osu_468994_6770584522.osr"));

        assertEquals("REANIMATE", osuBeatmap.getTitle());

        final ReplayAnalyze analyze = ReplayAnalyzer.analyze(osuBeatmap, osuReplay);
        final byte[] bytes = MissVisualizeService.visualizeMiss(analyze, 1);

        final Path out = Path.of("test-data/miss.png");
        Files.createDirectories(out.getParent());

        Files.write(out, bytes);
    }
}
