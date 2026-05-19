package xyz.zcraft.ostella.network.controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import xyz.zcraft.ostella.config.AppConfig;
import xyz.zcraft.ostella.data.score.ScoreType;
import xyz.zcraft.ostella.network.*;
import xyz.zcraft.ostella.service.AsyncService;
import xyz.zcraft.ostella.service.CacheService;
import xyz.zcraft.ostella.service.ReplayService;
import xyz.zcraft.ostella.util.BeatmapUtil;
import xyz.zcraft.ostella.util.TokenManager;
import xyz.zcraft.osu.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static xyz.zcraft.ostella.util.RequestUtil.*;

public class ReplayController {
    private static final Logger LOG = LogManager.getLogger(ReplayController.class);
    private final TokenManager tokenManager;
    private final ReplayService replayService;
    private final AsyncService executor;
    private final CacheService cacheService;
    private final AppConfig conf;
    private final Router router;
    private final Gson GSON = new Gson();

    public ReplayController(Router router) {
        this.router = router;
        this.conf = router.conf;
        this.replayService = router.replayService;
        this.tokenManager = router.tokenManager;
        this.cacheService = router.cacheService;
        this.executor = router.executor;
    }

    public void queueReplayRender(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            queueReplayRenderOfRefAsync(context);
        } else if (context.queryParam("m") != null) {
            queueReplayRenderOfBeatmapAsync(context);
        } else if (context.queryParam("ms") != null) {
            queueReplayRenderOfBeatmapsetAsync(context);
        } else {
            queueReplayRenderOfIdAsync(context);
        }
    }

    public void queueShowcaseRender(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            renderShowcaseOfUsersRefAsync(context);
        } else if (context.queryParam("u") != null) {
            renderShowcaseOfUsersAsync(context);
        } else {
            renderShowcaseOfIdsAsync(context);
        }
    }

    public void getReplayRenderStatus(@NotNull Context context) {
        String jobId = context.pathParam("jobId");
        ReplayService.JobStatus status = replayService.getJobStatus(jobId);

        switch (status) {
            case ReplayService.JobStatus.DONE -> context.status(200).result(
                    new Response(true, "Render complete!",
                            GSON.toJsonTree(Map.of(
                                    "status", "done",
                                    "id", jobId
                            ))).toString());
            case ReplayService.JobStatus.FAILED -> context.status(200).result(
                    new Response(true, "Render failed",
                            GSON.toJsonTree(Map.of(
                                    "status", "failed",
                                    "id", jobId
                            ))).toString());
            case ReplayService.JobStatus.TIMEOUT -> context.status(200).result(
                    new Response(true, "Render timed out",
                            GSON.toJsonTree(Map.of(
                                    "status", "timeout",
                                    "id", jobId
                            ))).toString());
            case ReplayService.JobStatus.QUEUED -> context.status(200).result(
                    new Response(true, "Render is waiting in queue",
                            GSON.toJsonTree(Map.of(
                                    "status", "queued",
                                    "id", jobId
                            ))).toString());
            case ReplayService.JobStatus.RENDERING -> {
                final ReplayService.JobProgress jobProgress = replayService.getJobProgress(jobId);
                JsonObject obj = new JsonObject();
                obj.addProperty("status", "rendering");
                obj.addProperty("id", jobId);
                if (jobProgress != null) {
                    obj.addProperty("progress", jobProgress.progress());
                    obj.addProperty("speed", jobProgress.speed());
                    obj.addProperty("eta", jobProgress.eta());
                }
                context.status(200).result(
                        new Response(true, "Render in progress", obj).toString());
            }
            default -> context.status(404).result(new Response(false, "Job not found", null).toString());
        }
    }

    public void getReplayRenderResultStream(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");
        Path video = replayService.getJobResult(jobId);

        if (video != null && Files.exists(video)) {
            context.writeSeekableStream(Files.newInputStream(video), "video/mp4");
        } else {
            context.status(404).result("Video expired or not found");
        }
    }

    public void getReplayRenderResultFile(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");
        Path video = replayService.getJobResult(jobId);

        if (video != null && Files.exists(video)) {
            context.result(Files.newInputStream(video));
        } else {
            context.status(404).result("Video expired or not found");
        }
    }

    public void deleteReplayRenderResult(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");

        Path video = replayService.getJobResult(jobId);

        replayService.removeJobProgress(jobId);
        replayService.removeJobResult(jobId);

        if (video != null && Files.exists(video)) {
            Files.deleteIfExists(video);
        }

        context.status(200).result("Job cleaned up successfully");
    }

    private void queueReplayRenderOfBeatmapsetAsync(@NotNull Context context) {
        context.future(() ->
                router.getScoreFromBeatmapsetAsync(context)
                        .thenCompose(score -> {
                            if (score == null) {
                                throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No score found for this user in the specified beatmapset!");
                            }

                            final double start = optionalDouble(context, "start");
                            final double end = optionalDouble(context, "end");

                            if (score.getPp() == null) {
                                score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                            }

                            return renderScoreForAsync(context, score, start, end);
                        })
        );
    }

    private void queueReplayRenderOfBeatmapAsync(@NotNull Context context) {
        final String m = requireNumberString(context, "m");
        final String u = requireNumberString(context, "u");

        context.future(() ->
                executor.enqueueAsync(() ->
                        OsuAPI.getUserScore(tokenManager.getTokenData(), u, m)
                ).thenCompose(score -> {
                    if (score == null) {
                        throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No score found for this user on this map!");
                    }

                    final double start = optionalDouble(context, "start");
                    final double end = optionalDouble(context, "end");

                    if (score.getPp() == null) {
                        score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                    }

                    return renderScoreForAsync(context, score, start, end);
                })
        );
    }

    private void queueReplayRenderOfRefAsync(@NotNull Context context) {
        context.future(() ->
                router.getScoreFromRefAsync(context)
                        .thenCompose(score -> {
                            if (score == null) {
                                throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found for reference!");
                            }

                            final double start = optionalDouble(context, "start");
                            final double end = optionalDouble(context, "end");

                            if (score.getPp() == null) {
                                score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                            }

                            return renderScoreForAsync(context, score, start, end);
                        })
        );
    }

    private void queueReplayRenderOfIdAsync(@NotNull Context context) {
        final String s = requireString(context, "s");

        context.future(() ->
                executor.enqueueAsync(() ->
                        OsuAPI.getScore(tokenManager.getTokenData(), s)
                ).thenCompose(score -> {
                    if (score == null) {
                        throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No score found for this ID!");
                    }

                    final double start = optionalDouble(context, "start");
                    final double end = optionalDouble(context, "end");

                    if (score.getPp() == null) {
                        score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                    }

                    return renderScoreForAsync(context, score, start, end);
                })
        );
    }

    public void getReplayRenderOverview(@NotNull Context context) {
        context.status(200).result(String.valueOf(new Response(true, "", GSON.toJsonTree(Map.of(
                "enabled", replayService != null,
                "queue", replayService != null ? replayService.getQueueSize() : 0
        )))));
    }

    private void renderShowcaseOfIdsAsync(@NotNull Context context) {
        if (replayService == null) return;

        final String ss = requireString(context, "s");
        final List<String> scoreIds = Arrays.stream(ss.split(",")).distinct().toList();

        context.future(() -> {
            List<CompletableFuture<Score>> scoreFutures = scoreIds.stream()
                    .map(id -> executor.enqueueAsync(() ->
                            OsuAPI.getScore(tokenManager.getTokenData(), id)) //
                    ).toList();

            return finalizeScoreRender(context, scoreFutures);
        });
    }

    private void renderShowcaseOfUsersAsync(@NotNull Context context) {
        if (replayService == null) return;

        final String us = requireString(context, "u");
        final String m = requireString(context, "m");

        context.future(() -> {
            final List<String> userIds = Arrays.stream(us.split(",")).distinct().toList();
            LOG.info("Getting {} scores for showcase on map {}", userIds.size(), m);

            List<CompletableFuture<Score>> scoreFutures = userIds.stream()
                    .map(userId -> executor.enqueueAsync(() ->
                            OsuAPI.getUserScore(tokenManager.getTokenData(), userId, m))).toList();

            return finalizeScoreRender(context, scoreFutures);
        });
    }

    @NonNull
    private CompletableFuture<?> finalizeScoreRender(@NotNull Context context, List<CompletableFuture<Score>> scoreFutures) {
        return CompletableFuture.allOf(scoreFutures.toArray(new CompletableFuture[0]))
                .thenApply(_ -> scoreFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(s -> s != null && s.getHasReplay())
                        .peek(score -> {
                            if (score.getPp() == null) {
                                final String rosuBeatmapPath = cacheService.getRosuBeatmapPath(String.valueOf(score.getBeatmap().getId()), false);
                                score.setPp(BeatmapUtil.estimatePp(score, rosuBeatmapPath));
                            }
                        })
                        .collect(Collectors.toCollection(LinkedList::new))
                )
                .thenCompose(validScores -> {
                    final double start = optionalDouble(context, "start");
                    final double end = optionalDouble(context, "end");

                    return renderShowcaseForAsync(context, validScores, start, end);
                });
    }

    private void renderShowcaseOfUsersRefAsync(@NotNull Context context) {
        final String us = requireString(context, "u");
        final String of = requireStringFrom(context, "of", "rs", "bo");
        final String uSource = requireString(context, "us");
        final int i = requireInt(context, "i");

        context.future(() ->
                executor.enqueueAsync(() ->
                        OsuAPI.getUserScores(tokenManager.getTokenData(), uSource,
                                of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, i, false)
                ).thenCompose(scores -> {
                    if (scores == null || scores.size() < i) {
                        throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found for user!");
                    }

                    final Score scoreSource = scores.get(i - 1);
                    final String beatmapId = String.valueOf(scoreSource.getBeatmap().getId());
                    final List<String> userIds = Arrays.stream(us.split(",")).distinct().toList();

                    List<CompletableFuture<Score>> scoreFutures = userIds.stream()
                            .map(userId -> executor.enqueueAsync(() ->
                                    OsuAPI.getUserScore(tokenManager.getTokenData(), userId, beatmapId))
                            ).toList();

                    return CompletableFuture.allOf(scoreFutures.toArray(new CompletableFuture[0]))
                            .thenApply(_ -> scoreFutures.stream()
                                    .map(CompletableFuture::join)
                                    .filter(s -> s != null && s.getHasReplay())
                                    .peek(score -> {
                                        if (score.getPp() == null) {
                                            score.setPp(BeatmapUtil.estimatePp(score, router.getRosuPath(score.getBeatmap().getId())));
                                        }
                                    })
                                    .collect(Collectors.toCollection(LinkedList::new))
                            );
                }).thenCompose(validScores -> {
                    final double start = optionalDouble(context, "start");
                    final double end = optionalDouble(context, "end");

                    return renderShowcaseForAsync(context, validScores, start, end);
                })
        );
    }

    private CompletableFuture<Void> renderScoreForAsync(@NotNull Context context, Score score, Double start, Double end) {
        if (replayService == null) return CompletableFuture.completedFuture(null);

        if (!score.getHasReplay()) {
            throw new ApiException(ErrorCode.REPLAY_UNAVAILABLE, "Replay unavailable!");
        }

        return executor.enqueueAsync(() -> {
            if (!cacheService.cacheBeatmapset(String.valueOf(score.getBeatmapset().getId()))) {
                throw new ApiException(ErrorCode.BEATMAPSET_FETCH_FAILED, "Failed to cache beatmapset!");
            }
            return null;
        }).thenCompose(_ ->
                executor.enqueueAsync(() -> {
                    try {
                        return cacheService.getReplay(tokenManager.getTokenData(), String.valueOf(score.getId()));
                    } catch (Exception e) {
                        throw new ApiException(ErrorCode.REPLAY_FETCH_FAILED, "Failed to cache replay for score id: " + score.getId(), e);
                    }
                })
        ).thenAccept(replayPath -> {
            final int queueSize = replayService.getQueueSize() + 1;

            if (queueSize > conf.replayRender().renderQueueSize()) {
                throw new ApiException(ErrorCode.RENDER_QUEUE_FULL, "Replay rendering queue is full!");
            }

            final String jobId = replayService.queueRender(replayPath, start, end);

            context.status(202).result(
                    new Response(
                            true,
                            "Replay render queued!",
                            GSON.toJsonTree(Map.of(
                                    "status", "queued",
                                    "position", queueSize,
                                    "id", jobId,
                                    "beatmap", Map.of(
                                            "title", score.getBeatmapset().getTitle(),
                                            "artist", score.getBeatmapset().getArtist(),
                                            "version", score.getBeatmap().getVersion(),
                                            "star", String.format("%.2f★", score.getBeatmap().getDifficultyRating())
                                    ),
                                    "scores", router.getScoresArr(List.of(score))
                            ))
                    ).toString()
            );
        });
    }

    private CompletableFuture<Void> renderShowcaseForAsync(@NotNull Context context, LinkedList<Score> scores, Double start, Double end) {
        if (replayService == null) return CompletableFuture.completedFuture(null);

        if (scores.isEmpty()) {
            throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No valid scores found!");
        }

        final long beatmapId = scores.getFirst().getBeatmap().getId();
        final long beatmapsetId = scores.getFirst().getBeatmap().getBeatmapsetId();

        CompletableFuture<Boolean> cacheFuture = executor.enqueueAsync(() ->
                cacheService.cacheBeatmapset(String.valueOf(beatmapsetId)));

        CompletableFuture<BeatmapExtended> beatmapFuture = executor.enqueueAsync(() ->
                OsuAPI.getBeatmap(tokenManager.getTokenData(), String.valueOf(beatmapId)));

        return CompletableFuture.allOf(cacheFuture, beatmapFuture).thenCompose(_ -> {
            if (!cacheFuture.join())
                throw new ApiException(ErrorCode.BEATMAPSET_FETCH_FAILED, "Failed to cache beatmapset!");
            BeatmapExtended beatmap = beatmapFuture.join();
            if (beatmap == null) throw new ApiException(ErrorCode.BEATMAP_FETCH_FAILED, "Failed to get beatmap!");

            List<CompletableFuture<Path>> replayFutures = scores.stream()
                    .map(score -> executor.enqueueAsync(() -> {
                        try {
                            return cacheService.getReplay(tokenManager.getTokenData(), String.valueOf(score.getId()));
                        } catch (Exception e) {
                            LOG.error("Failed to cache replay for score id: {}", score.getId(), e);
                            return null;
                        }
                    }))
                    .toList();

            return CompletableFuture.allOf(replayFutures.toArray(new CompletableFuture[0]))
                    .thenAccept(_ -> {
                        LinkedList<Path> replays = replayFutures.stream()
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedList::new));

                        final int queueSize = replayService.getQueueSize() + 1;
                        if (queueSize > conf.replayRender().renderQueueSize()) {
                            throw new ApiException(ErrorCode.RENDER_QUEUE_FULL, "Render queue full!");
                        }

                        final String jobId = replayService.queueRenderShowcase(String.valueOf(beatmapId), replays, start, end);

                        context.status(202).result(new Response(true, "Replay render queued!",
                                GSON.toJsonTree(Map.of(
                                        "status", "queued",
                                        "position", queueSize,
                                        "id", jobId,
                                        "beatmap", Map.of(
                                                "title", beatmap.getBeatmapset().getTitle(),
                                                "artist", beatmap.getBeatmapset().getArtist(),
                                                "version", beatmap.getVersion(),
                                                "star", String.format("%.2f★", beatmap.getDifficultyRating())
                                        ),
                                        "scores", router.getScoresArr(scores)
                                ))).toString());
                    });
        });
    }
}
