package xyz.zcraft.ostella.network.controller;

import com.google.gson.Gson;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import xyz.zcraft.ostella.network.*;
import xyz.zcraft.ostella.service.AsyncService;
import xyz.zcraft.ostella.service.CacheService;
import xyz.zcraft.ostella.service.RenderService;
import xyz.zcraft.ostella.util.TokenManager;
import xyz.zcraft.osu.model.*;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static xyz.zcraft.ostella.util.RequestUtil.*;

public class BeatmapsetController {
    public final RenderService renderer;
    public final AsyncService executor;
    public final TokenManager tokenManager;
    public final Router router;
    public final CacheService cacheService;

    public BeatmapsetController(Router router) {
        this.router = router;
        this.renderer = router.renderer;
        this.tokenManager = router.tokenManager;
        this.executor = router.executor;
        this.cacheService = router.cacheService;
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

    private void downloadBeatmapsetOfRef(@NotNull Context context) {
        final String of = requireString(context, "of");
        if ("mp".equals(of)) {
            downloadBeatmapsetFromCurrentRoom(context);
        } else {
            downloadBeatmapsetFromSomeScore(context);
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
                    if (room.getCurrentPlaylistItem() == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                    return executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), String.valueOf(room.getCurrentPlaylistItem().getBeatmapId())));
                })
                .thenApplyAsync(beatmapset -> finalizeBeatmapset(beatmapset, context), renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void downloadBeatmapsetFromCurrentRoom(@NonNull Context context) {
        final String auth = context.header("Authorization");

        if (auth == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getCurrentRoom(auth))
                .thenAccept(room -> {
                    if (room == null) throw new ApiException(ErrorCode.NO_ROOM_FOUND);
                    final Long beatmapsetId = room.getCurrentPlaylistItem().getBeatmap().getBeatmapsetId();
                    context.contentType("application/zip");
                    context.header("Content-Disposition", "attachment; filename=\"" + beatmapsetId + ".osz\"");
                    try {
                        cacheService.extractBeatmapset(String.valueOf(beatmapsetId), context.outputStream());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
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

    private void downloadBeatmapsetFromSomeScore(@NonNull Context context) {
        context.future(() -> router.getScoreFromRefAsync(context)
                .thenAccept(score -> {
                    if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                    final Long beatmapsetId = score.getBeatmap().getBeatmapsetId();
                    context.contentType("application/zip");
                    context.header("Content-Disposition", "attachment; filename=\"" + beatmapsetId + ".osz\"");
                    try {
                        cacheService.extractBeatmapset(String.valueOf(beatmapsetId), context.outputStream());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    private void getBeatmapsetOfMapAsync(@NotNull Context context) {
        final String m = requireNumberString(context, "m");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m))
                .thenApplyAsync(beatmapset -> finalizeBeatmapset(beatmapset, context), renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void downloadBeatmapsetOfMap(@NotNull Context context) {
        final String m = requireNumberString(context, "m");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m))
                .thenAccept(beatmapset -> {
                    context.contentType("application/zip");
                    context.header("Content-Disposition", "attachment; filename=\"" + beatmapset.getId() + ".osz\"");
                    try {
                        cacheService.extractBeatmapset(String.valueOf(beatmapset.getId()), context.outputStream());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    private void getBeatmapsetOfIdAsync(@NotNull Context context) {
        final String ms = requireNumberString(context, "ms");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), ms))
                .thenApplyAsync(beatmapset -> finalizeBeatmapset(beatmapset, context), renderer.getRenderExecutor())
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    private void downloadBeatmapsetOfId(@NotNull Context context) {
        final String ms = requireNumberString(context, "ms");
        context.future(() -> executor.enqueueAsync(() -> {
            context.contentType("application/zip");
            context.header("Content-Disposition", "attachment; filename=\"" + ms + ".osz\"");
            try {
                cacheService.extractBeatmapset(String.valueOf(ms), context.outputStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }));
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

    public void downloadBeatmapset(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            downloadBeatmapsetOfRef(context);
        } else if (context.queryParam("m") != null) {
            downloadBeatmapsetOfMap(context);
        } else {
            downloadBeatmapsetOfId(context);
        }
    }

    public void lookupBeatmapset(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            final String of = requireString(context, "of");
            if ("mp".equals(of)) {
                final String auth = context.header("Authorization");

                if (auth == null) {
                    throw new ApiException(ErrorCode.UNAUTHORIZED);
                }

                context.future(() -> executor
                        .enqueueAsync(() -> OsuAPI.getCurrentRoom(auth))
                        .thenAccept(room -> {
                            if (room == null) throw new ApiException(ErrorCode.NO_ROOM_FOUND);
                            final Long beatmapsetId = room.getCurrentPlaylistItem().getBeatmap().getBeatmapsetId();
                            context.status(200).result(
                                    new Response(
                                            true,
                                            "Lookup successful",
                                            new Gson().toJsonTree(Map.of("id", beatmapsetId))
                                    ).toString()
                            );
                        }));
            } else {
                context.future(() -> router.getScoreFromRefAsync(context)
                        .thenAccept(score -> {
                            if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                            final Long beatmapsetId = score.getBeatmap().getBeatmapsetId();
                            context.status(200).result(
                                    new Response(
                                            true,
                                            "Lookup successful",
                                            new Gson().toJsonTree(Map.of("id", beatmapsetId))
                                    ).toString()
                            );
                        }));
            }
        } else if (context.queryParam("m") != null) {
            final String m = requireNumberString(context, "m");

            context.future(() -> executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m))
                    .thenAccept(beatmapset -> context.status(200).result(
                            new Response(
                                    true,
                                    "Lookup successful",
                                    new Gson().toJsonTree(Map.of("id", beatmapset.getId()))
                            ).toString()
                    )));
        } else {
            final int ms = requireInt(context, "ms");
            context.status(200).result(
                    new Response(
                            true,
                            "Lookup successful",
                            new Gson().toJsonTree(Map.of("id", ms))
                    ).toString()
            );
        }
    }
}
