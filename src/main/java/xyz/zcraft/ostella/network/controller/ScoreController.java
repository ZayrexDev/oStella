package xyz.zcraft.ostella.network.controller;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.ostella.network.ApiException;
import xyz.zcraft.ostella.network.ErrorCode;
import xyz.zcraft.ostella.network.OsuAPI;
import xyz.zcraft.ostella.network.Router;
import xyz.zcraft.ostella.service.AsyncService;
import xyz.zcraft.ostella.service.RenderService;
import xyz.zcraft.ostella.util.TokenManager;
import xyz.zcraft.ostella.util.format.ScoreFormatUtil;
import xyz.zcraft.osu.model.BeatmapExtended;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.model.Score;
import xyz.zcraft.osu.parser.DiffSpec;
import xyz.zcraft.osu.parser.OsuParser;

import static xyz.zcraft.ostella.util.RequestUtil.requireNumberString;

public class ScoreController {
    public final RenderService renderer;
    public final AsyncService executor;
    public final TokenManager tokenManager;
    public final Router router;

    public ScoreController(Router router) {
        this.router = router;
        this.renderer = router.renderer;
        this.executor = router.executor;
        this.tokenManager = router.tokenManager;
    }

    public void getScore(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            getScoreOfRefAsync(context);
        } else if (context.queryParam("m") != null) {
            getScoreOfBeatmapAsync(context);
        } else if (context.queryParam("ms") != null) {
            getScoreOfBeatmapsetAsync(context);
        } else {
            getScoreOfIdAsync(context);
        }
    }

    private void getScoreOfIdAsync(@NotNull Context context) {
        final String s = requireNumberString(context, "s");
        context.future(() -> router.getScore(s)
                .thenApplyAsync(score -> {
                    if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                    final BeatmapExtended beatmap = score.getBeatmap();

                    context.header("X-Beatmap-Id", String.valueOf(beatmap.getId()))
                            .header("X-Score-Id", String.valueOf(score.getId()));

                    final DiffSpec diffSpec = OsuParser.getDiffSpecForMap(beatmap, router.getRosuPath(beatmap.getId()), ScoreFormatUtil.getModsList(score).stream().map(Mod::getAcronym).reduce("", String::concat));

                    if (score.getPp() == null) {
                        score.setPp(OsuParser.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                    }

                    return renderer.renderScore(score, diffSpec);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getScoreOfBeatmapAsync(@NotNull Context context) {
        final String m = requireNumberString(context, "m");
        final String u = requireNumberString(context, "u");

        context.header("X-Beatmap-Id", m);

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getUserScore(tokenManager.getTokenData(), u, m))
                .thenCompose(score -> {
                            if (score == null) {
                                throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                            }

                            context.header("X-Score-Id", String.valueOf(score.getId()));
                            return executor
                                    .enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), String.valueOf(score.getBeatmap().getBeatmapsetId())))
                                    .thenApply(beatmapset -> {
                                        score.getBeatmap().setBeatmapset(beatmapset);
                                        score.setBeatmapset(beatmapset);
                                        return score;
                                    });
                        }
                )
                .thenApplyAsync(this::finalizeScore, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getScoreOfRefAsync(@NotNull Context context) {
        context.future(() -> router.getScoreFromRefAsync(context)
                .thenApply(Score::getId)
                .thenApply(String::valueOf)
                .thenCompose(router::getScore)
                .thenApplyAsync(score -> {
                    context.header("X-Beatmap-Id", String.valueOf(score.getBeatmap().getId()))
                            .header("X-Score-Id", String.valueOf(score.getId()));

                    return finalizeScore(score);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private byte[] finalizeScore(Score score) {
        final DiffSpec diffSpec = OsuParser.getDiffSpecForMap(
                score.getBeatmap(),
                router.getRosuPath(score.getBeatmap().getId()),
                score.getMods().stream().reduce("", String::concat)
        );

        if (score.getPp() == null) {
            score.setPp(OsuParser.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
        }

        return renderer.renderScore(score, diffSpec);
    }

    private void getScoreOfBeatmapsetAsync(@NotNull Context context) {
        context.future(() -> router.getScoreFromBeatmapsetAsync(context)
                .thenApplyAsync(score -> {
                    context.header("X-Score-Id", String.valueOf(score.getId()));
                    return finalizeScore(score);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }
}
