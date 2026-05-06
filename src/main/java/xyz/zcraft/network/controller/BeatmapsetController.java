package xyz.zcraft.network.controller;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.network.ApiException;
import xyz.zcraft.network.ErrorCode;
import xyz.zcraft.network.OsuAPI;
import xyz.zcraft.network.Router;
import xyz.zcraft.service.AsyncService;
import xyz.zcraft.service.RenderService;
import xyz.zcraft.util.TokenManager;

import static xyz.zcraft.util.RequestUtil.requireNumberString;

public class BeatmapsetController {
    public final RenderService renderer;
    public final AsyncService executor;
    public final TokenManager tokenManager;
    public final Router router;

    public BeatmapsetController(Router router) {
        this.router = router;
        this.renderer = router.renderer;
        this.tokenManager = router.tokenManager;
        this.executor = router.executor;
    }

    public void getBeatmapsetAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            getBeatmapsetOfRefAsync(context);
        } else if (context.queryParam("m") != null) {
            getBeatmapsetOfMapAsync(context);
        } else {
            getBeatmapsetOfIdAsync(context);
        }
    }

    private void getBeatmapsetOfRefAsync(@NotNull Context context) {
        context.future(() -> router.getScoreFromRefAsync(context)
                .thenCompose(score -> {
                    if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);

                    return executor.enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), String.valueOf(score.getBeatmapset().getId())));
                })
                .thenApplyAsync(beatmapset -> {
                    if (beatmapset == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                    context.header("X-Beatmapset-Id", beatmapset.getId().toString());
                    return renderer.renderBeatmapset(beatmapset);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getBeatmapsetOfMapAsync(@NotNull Context context) {
        final String m = requireNumberString(context, "m");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m))
                .thenApplyAsync(ms -> {
                    if (ms == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                    context.header("X-Beatmapset-Id", ms.getId().toString());
                    return renderer.renderBeatmapset(ms);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getBeatmapsetOfIdAsync(@NotNull Context context) {
        final String ms = requireNumberString(context, "ms");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), ms))
                .thenApplyAsync(beatmapset -> {
                    if (beatmapset == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                    context.header("X-Beatmapset-Id", beatmapset.getId().toString());
                    return renderer.renderBeatmapset(beatmapset);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }
}
