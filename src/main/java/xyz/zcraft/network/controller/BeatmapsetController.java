package xyz.zcraft.network.controller;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import xyz.zcraft.model.beatmap.Beatmap;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.Beatmapset;
import xyz.zcraft.network.ApiException;
import xyz.zcraft.network.ErrorCode;
import xyz.zcraft.network.OsuAPI;
import xyz.zcraft.network.Router;
import xyz.zcraft.service.AsyncService;
import xyz.zcraft.service.RenderService;
import xyz.zcraft.util.TokenManager;

import java.util.Comparator;
import java.util.stream.Collectors;

import static xyz.zcraft.util.RequestUtil.requireNumberString;
import static xyz.zcraft.util.RequestUtil.requireString;

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

    public void getBeatmapset(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            getBeatmapsetOfRefAsync(context);
        } else if (context.queryParam("m") != null) {
            getBeatmapsetOfMapAsync(context);
        } else {
            getBeatmapsetOfIdAsync(context);
        }
    }

    private void getBeatmapsetOfRefAsync(@NotNull Context context) {
        final String of = requireString(context, "of");
        if ("mp".equals(of)) {
            getBeatmapsetFromCurrentRoom(context);
        } else {
            getBeatmapsetFromSomeScore(context);
        }
    }

    private void getBeatmapsetFromCurrentRoom(@NonNull Context context) {
        final String auth = context.header("Authorization");

        if (auth == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getCurrentRoom(auth))
                .thenCompose(room -> {
                    if (room == null) throw new ApiException(ErrorCode.NO_ROOM_FOUND);
                    return executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), String.valueOf(room.getCurrentPlaylistItem().getBeatmapId())));
                })
                .thenApplyAsync(beatmapset -> finalizeBeatmapset(beatmapset, context), renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getBeatmapsetFromSomeScore(@NonNull Context context) {
        context.future(() -> router.getScoreFromRefAsync(context)
                .thenCompose(score -> {
                    if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                    return executor.enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), String.valueOf(score.getBeatmapset().getId())));
                })
                .thenApplyAsync(beatmapset -> finalizeBeatmapset(beatmapset, context), renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getBeatmapsetOfMapAsync(@NotNull Context context) {
        final String m = requireNumberString(context, "m");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m))
                .thenApplyAsync(beatmapset -> finalizeBeatmapset(beatmapset, context), renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getBeatmapsetOfIdAsync(@NotNull Context context) {
        final String ms = requireNumberString(context, "ms");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), ms))
                .thenApplyAsync(beatmapset -> finalizeBeatmapset(beatmapset, context), renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private byte[] finalizeBeatmapset(Beatmapset beatmapset, Context context) {
        if (beatmapset == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
        beatmapset.getBeatmaps().sort(Comparator.comparingDouble(Beatmap::getDifficultyRating));
        context.header("X-Beatmapset-Id", beatmapset.getId().toString())
                .header("X-Beatmap-Ids", beatmapset.getBeatmaps().stream()
                        .map(BeatmapExtended::getId)
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")))
                .header("X-Beatmap-Stars", beatmapset.getBeatmaps().stream()
                        .map(BeatmapExtended::getDifficultyRating)
                        .map(d -> String.format("%.2f", d))
                        .collect(Collectors.joining(",")));

        return renderer.renderBeatmapset(beatmapset);
    }
}
