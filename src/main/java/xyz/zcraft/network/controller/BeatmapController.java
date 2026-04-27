package xyz.zcraft.network.controller;

import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.model.Mod;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.DiffSpec;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.network.ApiException;
import xyz.zcraft.network.ErrorCode;
import xyz.zcraft.network.OsuAPI;
import xyz.zcraft.network.Router;
import xyz.zcraft.service.AsyncService;
import xyz.zcraft.service.RenderService;
import xyz.zcraft.util.TokenManager;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static xyz.zcraft.util.BeatmapUtil.getDiffSpecForMap;
import static xyz.zcraft.util.RequestUtil.*;
import static xyz.zcraft.util.RequestUtil.requireNumberString;

public class BeatmapController {
    final RenderService renderer;
    final AsyncService executor;
    final TokenManager tokenManager;
    final Router router;

    public BeatmapController(Router router) {
        this.router = router;
        this.renderer = router.renderer;
        this.tokenManager = router.tokenManager;
        this.executor = router.executor;
    }

    public void getBeatmapAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            getBeatmapOfRefAsync(context);
        } else if (context.queryParam("ms") != null) {
            getBeatmapOfSetAsync(context);
        } else {
            getBeatmapOfIdAsync(context);
        }
    }

    private void getBeatmapOfSetAsync(@NotNull Context context) {
        final String ms = requireNumberString(context, "ms");
        final int i = requireInt(context, "i");
        final String mod = optionalString(context, "mod");

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), ms))
                .thenApply(beatmapset -> {
                    if (beatmapset == null)
                        throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND, "No beatmapset found");
                    final List<BeatmapExtended> beatmaps = beatmapset.getBeatmaps();
                    beatmaps.sort(Comparator.comparingDouble(BeatmapExtended::getDifficultyRating));
                    final BeatmapExtended beatmapExtended = beatmaps.get(i - 1);
                    beatmapExtended.setBeatmapset(beatmapset);
                    return beatmapExtended;
                })
                .thenApplyAsync(beatmapExtended -> {
                    DiffSpec spec = getDiffSpecForMap(beatmapExtended, router.getRosuPath(beatmapExtended.getId()), mod);
                    return renderer.renderBeatmap(beatmapExtended, spec);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getBeatmapOfIdAsync(@NotNull Context context) {
        final String m = requireNumberString(context, "m");
        final String mod = optionalString(context, "mod");

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m))
                .thenApply(beatmapset -> {
                    if (beatmapset == null)
                        throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND, "No beatmapset found");
                    final BeatmapExtended beatmapExtended = beatmapset.getBeatmaps()
                            .stream()
                            .filter(b -> Objects.equals(String.valueOf(b.getId()), m))
                            .findFirst()
                            .orElseThrow(() -> new ApiException(ErrorCode.NO_BEATMAP_FOUND, "No beatmap found"));
                    beatmapExtended.setBeatmapset(beatmapset);
                    return beatmapExtended;
                })
                .thenApplyAsync(beatmap -> {
                    DiffSpec diffSpec = getDiffSpecForMap(beatmap, router.getRosuPath(beatmap.getId()), mod);
                    return renderer.renderBeatmap(beatmap, diffSpec);
                }, renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void getBeatmapOfRefAsync(@NotNull Context context) {
        final String of = requireStringFrom(context, "of", "rs", "bo");
        final int i = requireInt(context, "i");
        final String u = requireNumberString(context, "u");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getUserScores(tokenManager.getTokenData(), u, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, i, false))
                .thenCompose(scores -> {
                    if (scores == null || scores.isEmpty())
                        throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found");
                    final var beatmapId = scores.getLast().getBeatmap().getId();
                    final var beatmapsetId = scores.getLast().getBeatmapset().getId();
                    return executor
                            .enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), String.valueOf(beatmapsetId)))
                            .thenApply(beatmapset -> {
                                if (beatmapset == null)
                                    throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND, "No beatmapset found");
                                final BeatmapExtended beatmapExtended = beatmapset.getBeatmaps()
                                        .stream().filter(b -> Objects.equals(b.getId(), beatmapId))
                                        .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NO_BEATMAP_FOUND, "No beatmap found"));
                                beatmapExtended.setBeatmapset(beatmapset);
                                return beatmapExtended;
                            })
                            .thenApplyAsync(beatmap -> {
                                final DiffSpec diffSpec = getDiffSpecForMap(beatmap, router.getRosuPath(beatmap.getId()), scores.getLast().getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));
                                return renderer.renderBeatmap(beatmap, diffSpec);
                            }, renderer.getRenderExecutor());
                })
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }
}
