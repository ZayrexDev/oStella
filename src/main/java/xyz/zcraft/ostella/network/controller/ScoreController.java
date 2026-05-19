package xyz.zcraft.ostella.network.controller;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.ostella.model.Mod;
import xyz.zcraft.ostella.model.beatmap.BeatmapExtended;
import xyz.zcraft.ostella.model.beatmap.DiffSpec;
import xyz.zcraft.ostella.model.score.Score;
import xyz.zcraft.ostella.network.ApiException;
import xyz.zcraft.ostella.network.ErrorCode;
import xyz.zcraft.ostella.network.OsuAPI;
import xyz.zcraft.ostella.network.Router;
import xyz.zcraft.ostella.service.AsyncService;
import xyz.zcraft.ostella.service.RenderService;
import xyz.zcraft.ostella.util.BeatmapUtil;
import xyz.zcraft.ostella.util.TokenManager;

import static xyz.zcraft.ostella.util.BeatmapUtil.getDiffSpecForMap;
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
        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getScore(tokenManager.getTokenData(), s))
                .thenApplyAsync(score -> {
                    if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                    final BeatmapExtended beatmap = score.getBeatmap();

                    context.header("X-Beatmap-Id", String.valueOf(beatmap.getId()))
                            .header("X-Score-Id", String.valueOf(score.getId()));

                    final DiffSpec diffSpec = getDiffSpecForMap(beatmap, router.getRosuPath(beatmap.getId()), score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));

                    if (score.getPp() == null) {
                        score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
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
                .thenApplyAsync(score -> {
                    final DiffSpec diffSpec = getDiffSpecForMap(
                            score.getBeatmap(), router.getRosuPath(score.getBeatmap().getId()),
                            score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat)
                    );

                    if (score.getPp() == null) {
                        score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                    }

                    return renderer.renderScore(score, diffSpec);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getScoreOfRefAsync(@NotNull Context context) {
        context.future(() -> router.getScoreFromRefAsync(context)
                .thenApply(Score::getId)
                .thenCompose(scoreId -> executor.enqueueAsync(() -> OsuAPI.getScore(tokenManager.getTokenData(), String.valueOf(scoreId))))
                .thenApplyAsync(score -> {
                    context.header("X-Beatmap-Id", String.valueOf(score.getBeatmap().getId()))
                            .header("X-Score-Id", String.valueOf(score.getId()));

                    final DiffSpec diffSpec = getDiffSpecForMap(score.getBeatmap(), router.getRosuPath(score.getBeatmap().getId()), score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));

                    if (score.getPp() == null) {
                        score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                    }

                    return renderer.renderScore(score, diffSpec);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getScoreOfBeatmapsetAsync(@NotNull Context context) {
        context.future(() -> router.getScoreFromBeatmapsetAsync(context)
                .thenApplyAsync(score -> {
                    context.header("X-Score-Id", String.valueOf(score.getId()));
                    final DiffSpec diffSpec = getDiffSpecForMap(
                            score.getBeatmap(), router.getRosuPath(score.getBeatmap().getId()),
                            score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat)
                    );

                    if (score.getPp() == null) {
                        score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                    }

                    return renderer.renderScore(score, diffSpec);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }
}
