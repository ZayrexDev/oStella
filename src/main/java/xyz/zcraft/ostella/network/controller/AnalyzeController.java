package xyz.zcraft.ostella.network.controller;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.ostella.network.ApiException;
import xyz.zcraft.ostella.network.ErrorCode;
import xyz.zcraft.ostella.network.Router;
import xyz.zcraft.ostella.service.AsyncService;
import xyz.zcraft.ostella.service.CacheService;
import xyz.zcraft.ostella.service.RenderService;
import xyz.zcraft.ostella.util.TokenManager;
import xyz.zcraft.osu.model.BeatmapExtended;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.model.Score;
import xyz.zcraft.osu.parser.BeatmapParser;
import xyz.zcraft.osu.parser.OsuParser;
import xyz.zcraft.osu.parser.ReplayAnalyzer;
import xyz.zcraft.osu.parser.ReplayParser;
import xyz.zcraft.osu.parser.data.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static xyz.zcraft.ostella.util.RequestUtil.requirePathLong;

public class AnalyzeController {
    final RenderService renderer;
    final AsyncService executor;
    final TokenManager tokenManager;
    final Router router;

    public AnalyzeController(Router router) {
        this.router = router;
        this.tokenManager = router.tokenManager;
        this.renderer = router.renderer;
        this.executor = router.executor;
    }

    public void renderScoreAnalysisById(@NotNull Context context) {
        final long scoreId = requirePathLong(context, "scoreId");
        context.future(() -> router.getScore(scoreId)
                .thenApply(score -> {
                    if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                    final BeatmapExtended beatmap = score.getBeatmap();

                    context.header("X-Beatmap-Id", String.valueOf(beatmap.getId()))
                            .header("X-Score-Id", String.valueOf(score.getId()));

                    final Path rosuPath = router.getRosuPath(beatmap.getId());
                    final DiffSpec diffSpec = OsuParser.getDiffSpecForMap(beatmap, rosuPath, score.getMods().stream().map(Mod::getAcronym).reduce("", String::concat));

                    if (score.getPp() == null) {
                        score.setPp(OsuParser.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                    }

                    try {
                        final OsuBeatmap osuBeatmap = BeatmapParser.parseBeatmap(rosuPath);
                        final OsuReplay osuReplay = ReplayParser.parseReplay(CacheService.getReplay(tokenManager.getTokenData(), score.getId()));

                        final ReplayAnalyze analyze = ReplayAnalyzer.analyze(osuBeatmap, osuReplay);

                        final List<Long> hitErrors = analyze.events().stream()
                                .filter(HitEvent::wasHit)
                                .map(HitEvent::hitTimeOffset)
                                .toList();

                        final List<double[]> hitPos = analyze.events().stream()
                                .filter(HitEvent::wasHit)
                                .filter(e -> e.hitObject().getObjectType() != HitObject.ObjectType.SPINNER)
                                .map(HitEvent::aimBias)
                                .filter(Objects::nonNull)
                                .map(HitEvent.AimBias::standardize)
                                .map(b -> new double[]{b.theta(), b.distance()})
                                .toList();

                        final List<double[]> missPos = analyze.events().stream()
                                .filter(hitEvent -> !hitEvent.wasHit())
                                .filter(e -> e.hitObject().getObjectType() != HitObject.ObjectType.SPINNER)
                                .map(HitEvent::aimBias)
                                .filter(Objects::nonNull)
                                .filter(b -> b.distance() < diffSpec.getCircleRadius() * 1.2)
                                .map(HitEvent.AimBias::standardize)
                                .map(b -> new double[]{b.theta(), b.distance()})
                                .toList();

                        final List<Double> aimBiases = analyze.events().stream()
                                .filter(HitEvent::wasHit)
                                .filter(e -> e.hitObject().getObjectType() != HitObject.ObjectType.SPINNER)
                                .map(HitEvent::aimBias)
                                .filter(Objects::nonNull)
                                .map(HitEvent.AimBias::standardize)
                                .map(b -> b.distance() * (Math.abs(b.theta() - Math.PI) >= (Math.PI / 2) ? 1 : -1))
                                .toList();

                        final double aimBias = aimBiases.isEmpty() ? 0.0 : (aimBiases.stream().reduce(0.0, Double::sum) / aimBiases.size() / diffSpec.getCircleRadius());

                        final double avgTimingError = hitErrors.isEmpty() ? 0.0 : (hitErrors.stream().reduce(0L, Long::sum) / (double) hitErrors.size());
                        return new ScoreAnalyzeData(score, diffSpec, hitErrors, hitPos, missPos, aimBias, avgTimingError, analyze);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .thenApplyAsync(renderer::renderScoreAnalysis, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    public record ScoreAnalyzeData(
            Score score,
            DiffSpec diffSpec,
            List<Long> hitErrors,
            List<double[]> hitPositions,
            List<double[]> missPositions,
            double aimBias,
            double avgTimingError,
            ReplayAnalyze replayAnalyze
    ) {
    }
}
