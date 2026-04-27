package xyz.zcraft.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import desu.life.RosuFFI;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.config.AppConfig;
import xyz.zcraft.model.Mod;
import xyz.zcraft.model.MultiplayerRoom;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.DiffSpec;
import xyz.zcraft.model.beatmap.SearchResultItem;
import xyz.zcraft.model.score.Placement;
import xyz.zcraft.model.score.Score;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.model.user.Statistics;
import xyz.zcraft.model.user.StatisticsRuleset;
import xyz.zcraft.model.user.User;
import xyz.zcraft.service.AsyncService;
import xyz.zcraft.service.CacheService;
import xyz.zcraft.service.RenderService;
import xyz.zcraft.service.ReplayService;
import xyz.zcraft.util.TokenManager;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static xyz.zcraft.util.BeatmapUtil.getDiffSpecForMap;
import static xyz.zcraft.util.RequestUtil.*;

public class Router implements Closeable {
    private static final Logger LOG = LogManager.getLogger(Router.class);
    private final Gson GSON = new Gson();
    private final RenderService renderer;
    private final AsyncService executor;
    private final TokenManager tokenManager;
    private final ReplayService replayService;
    private final CacheService cacheService;
    private final AppConfig conf;
    private final Helper helper;

    public Router(AppConfig conf, TokenManager tokenManager) throws IOException {
        this.conf = conf;
        this.tokenManager = tokenManager;
        this.executor = new AsyncService(conf.ostella().maxThreads(), conf.ostella().requestPerSecond());
        this.cacheService = new CacheService(executor);
        this.renderer = new RenderService(cacheService);
        if (conf.replayRender().enabled()) {
            this.replayService = new ReplayService(conf, cacheService.getDanserCache());
        } else {
            this.replayService = null;
        }

        this.helper = new Helper(this);
    }

    protected void getScoreAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            helper.getScoreOfRefAsync(context);
        } else if (context.queryParam("m") != null) {
            helper.getScoreOfBeatmapAsync(context);
        } else if (context.queryParam("ms") != null) {
            helper.getScoreOfBeatmapsetAsync(context);
        } else {
            helper.getScoreOfIdAsync(context);
        }
    }

    protected void searchBeatmapSetAsync(@NotNull Context context) {
        final String query = requireString(context, "q");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.searchBeatmapset(tokenManager.getTokenData(), query))
                .thenApply(result -> {
                    if (result == null || result.isEmpty()) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                    return result.stream().map(SearchResultItem::fromBeatmapset).toList();
                })
                .thenAccept(
                        result -> context.status(200).result(
                                new Response(true, "Query successful", GSON.toJsonTree(result)).toString()
                        )
                ));
    }

    protected void getLeaderBoardAsync(@NotNull Context context) {
        final String us = requireString(context, "u");
        final List<String> ids = Arrays.stream(us.split(",")).distinct().toList();

        List<CompletableFuture<List<User>>> futures = new ArrayList<>();

        for (int i = 0; i < ids.size(); i += 50) {
            final List<String> subList = ids.subList(i, Math.min(i + 50, ids.size()));

            futures.add(executor.enqueueAsync(() ->
                    OsuAPI.getUsers(tokenManager.getTokenData(), subList)
            ));
        }

        CompletableFuture<?>[] futuresArray = futures.toArray(new CompletableFuture[0]);
        context.future(() ->
                CompletableFuture.allOf(futuresArray)
                        .thenApply(_ ->
                                futures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(Objects::nonNull)
                                        .flatMap(List::stream)
                                        .collect(Collectors.toCollection(LinkedList::new)))
                        .thenApplyAsync(users -> {
                            users.sort(Comparator.comparingDouble((User user) ->
                                    Optional.ofNullable(user)
                                            .map(User::getStatisticsRulesets)
                                            .map(StatisticsRuleset::getOsu)
                                            .map(Statistics::getPp)
                                            .orElse(0.0)
                            ).reversed());
                            return renderer.renderLeaderboard(users);

                        }, renderer.getRenderExecutor())
                        .thenAccept(imgByte -> context.status(200).result(imgByte))
        );
    }

    protected void getPKAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            helper.getPKOfRefAsync(context);
        } else {
            helper.getPKOfIdsAsync(context);
        }
    }

    protected void getBeatmapAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            helper.getBeatmapOfRefAsync(context);
        } else if (context.queryParam("ms") != null) {
            helper.getBeatmapOfSetAsync(context);
        } else {
            helper.getBeatmapOfIdAsync(context);
        }
    }

    protected void getBeatmapsetAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            helper.getBeatmapsetOfRefAsync(context);
        } else if (context.queryParam("m") != null) {
            helper.getBeatmapsetOfMapAsync(context);
        } else {
            helper.getBeatmapsetOfIdAsync(context);
        }
    }

    protected void bypassRequest(@NotNull Context context) {
        executor.enqueueAsync(() -> OsuAPI.byPassRequest(tokenManager.getTokenData(), context.queryString()))
                .thenAccept(r -> context.status(200).result(new Response(true, "Bypass successful", r).toString()));
    }

    protected void getServerStatusAsync(@NotNull Context context) {
        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.isOsuApiHealthy(tokenManager.getTokenData()))
                .thenAccept(r -> context.status(200)
                        .result(new Response(
                                true,
                                "Server is running!",
                                GSON.toJsonTree(Map.of(
                                        "ostella", true,
                                        "osu-api", r
                                ))).toString())));

    }

    protected void getRecentScoresAsync(@NotNull Context context) {
        final String u = requireNumberString(context, "u");
        final String n = requireNumberString(context, "n");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getUserScores(
                        tokenManager.getTokenData(), u, ScoreType.RECENT, Integer.parseInt(n), false)
                )
                .thenCompose(scores -> executor.enqueueAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), u))
                        .thenApplyAsync(user -> {
                            if (user == null) {
                                throw new ApiException(ErrorCode.NO_USER_FOUND, "No user found");
                            }
                            return renderer.renderScores(user, scores, ScoreType.RECENT);
                        }, renderer.getRenderExecutor()))
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    protected void getMultiplayerRoomsAsync(@NotNull Context context) {
        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getRooms(tokenManager.getTokenData()))
                .thenApply(rooms -> {
                    if (rooms == null || rooms.isEmpty()) {
                        throw new ApiException(ErrorCode.NO_ROOM_FOUND, "No rooms found");
                    }

                    rooms.sort(Comparator.comparingInt(MultiplayerRoom::getParticipantCount));

                    List<MultiplayerRoom> topRooms = rooms.size() > 20 ? rooms.reversed().subList(0, 20) : rooms;
                    JsonArray arr = new JsonArray();
                    for (MultiplayerRoom room : topRooms) {
                        arr.add(room.getName() + " << " + room.getParticipantCount());
                    }
                    return arr;
                })
                .thenAccept(arr -> context.status(200).result(new Response(true, "Success", arr).toString())));
    }

    protected void getDailyAsync(@NotNull Context context) {
        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getRooms(tokenManager.getTokenData()))
                .thenApply(rooms -> {
                    if (rooms == null || rooms.isEmpty()) {
                        throw new ApiException(ErrorCode.NO_ROOM_FOUND, "No rooms found");
                    }
                    return rooms.stream().filter(room -> Objects.equals(room.getCategory(), "daily_challenge")).findFirst()
                            .orElseThrow(() -> new ApiException(ErrorCode.NO_ROOM_FOUND, "Daily challenge room not found!"));
                })
                .thenApply(room -> {
                    final BeatmapExtended beatmap = room.getCurrentPlaylistItem().getBeatmap();

                    JsonObject data = new JsonObject();
                    data.addProperty("name", room.getName());
                    data.addProperty("participant_count", room.getParticipantCount());
                    data.addProperty("title", beatmap.getBeatmapset().getTitle());
                    data.addProperty("difficulty_rating", beatmap.getDifficultyRating());
                    data.addProperty("version", beatmap.getVersion());

                    StringBuilder modStr = new StringBuilder();
                    for (Mod mod : room.getCurrentPlaylistItem().getRequiredMods()) {
                        modStr.append(mod.getAcronym()).append(" ");
                    }

                    data.addProperty("required_mods", modStr.toString().trim());
                    return data;
                })
                .thenAccept(data -> context.status(200).result(new Response(true, "Success", data).toString())));
    }

    protected void getBestOfNAsync(@NotNull Context context) {
        final String u = requireNumberString(context, "u");
        final String n = requireNumberString(context, "n");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getUserScores(
                        tokenManager.getTokenData(), u, ScoreType.BEST, Integer.parseInt(n), false
                ))
                .thenCompose(scores -> {
                    if (scores == null || scores.isEmpty()) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                    return executor.enqueueAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), u))
                            .thenApplyAsync(user -> {
                                if (user == null) throw new ApiException(ErrorCode.NO_USER_FOUND);
                                return renderer.renderScores(user, scores, ScoreType.BEST);
                            }, renderer.getRenderExecutor());
                })
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    protected void queueReplayRenderAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            helper.queueReplayRenderOfRefAsync(context);
        } else if (context.queryParam("m") != null) {
            helper.queueReplayRenderOfBeatmapAsync(context);
        } else if (context.queryParam("ms") != null) {
            helper.queueReplayRenderOfBeatmapsetAsync(context);
        } else {
            helper.queueReplayRenderOfIdAsync(context);
        }
    }

    protected void getReplayRenderStatus(@NotNull Context context) {
        String jobId = context.pathParam("jobId");
        ReplayService.JobStatus status = replayService.getJobStatus(jobId);

        switch (status) {
            case ReplayService.JobStatus.DONE -> context.status(200).result(
                    new Response(true, "Render complete!",
                            GSON.toJsonTree(Map.of(
                                    "status", "done"
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

    protected void getReplayRenderResultStream(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");
        Path video = replayService.getJobResult(jobId);

        if (video != null && Files.exists(video)) {
            context.writeSeekableStream(Files.newInputStream(video), "video/mp4");
        } else {
            context.status(404).result("Video expired or not found");
        }
    }

    protected void getReplayRenderResultFile(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");
        Path video = replayService.getJobResult(jobId);

        if (video != null && Files.exists(video)) {
            context.result(Files.newInputStream(video));
        } else {
            context.status(404).result("Video expired or not found");
        }
    }

    protected void deleteReplayRenderResult(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");

        Path video = replayService.getJobResult(jobId);

        replayService.removeJobProgress(jobId);
        replayService.removeJobResult(jobId);

        if (video != null && Files.exists(video)) {
            Files.deleteIfExists(video);
        }

        context.status(200).result("Job cleaned up successfully");
    }

    protected String getRosuPath(Long id) {
        return cacheService.getRosuBeatmapPath(String.valueOf(id), true);
    }

    @Override
    public void close() {
        executor.close();
        renderer.close();
        replayService.close();
    }

    public void queueShowcaseRenderAsync(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            helper.renderShowcaseOfUsersRefAsync(context);
        } else if (context.queryParam("u") != null) {
            helper.renderShowcaseOfUsersAsync(context);
        } else {
            helper.renderShowcaseOfIdsAsync(context);
        }
    }

    public void getReplayRenderOverview(@NotNull Context context) {
        context.status(200).result(String.valueOf(new Response(true, "", GSON.toJsonTree(Map.of(
                "enabled", replayService != null,
                "queue", replayService != null ? replayService.getQueueSize() : 0
        )))));
    }

    private record Helper(Router router) {
        private void queueReplayRenderOfBeatmapsetAsync(@NotNull Context context) {
            context.future(() ->
                    getScoreFromBeatmapsetAsync(context)
                            .thenCompose(score -> {
                                if (score == null) {
                                    throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No score found for this user in the specified beatmapset!");
                                }

                                final double start = optionalDouble(context, "start");
                                final double end = optionalDouble(context, "end");

                                return renderScoreForAsync(context, score, start, end);
                            })
            );
        }

        private void queueReplayRenderOfBeatmapAsync(@NotNull Context context) {
            final String m = requireNumberString(context, "m");
            final String u = requireNumberString(context, "u");

            context.future(() ->
                    router.executor.enqueueAsync(() ->
                            OsuAPI.getUserScore(router.tokenManager.getTokenData(), u, m)
                    ).thenCompose(score -> {
                        if (score == null) {
                            throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No score found for this user on this map!");
                        }

                        final double start = optionalDouble(context, "start");
                        final double end = optionalDouble(context, "end");

                        return renderScoreForAsync(context, score, start, end);
                    })
            );
        }

        private void queueReplayRenderOfRefAsync(@NotNull Context context) {
            context.future(() ->
                    getScoreFromRefAsync(context)
                            .thenCompose(score -> {
                                if (score == null) {
                                    throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found for reference!");
                                }

                                final double start = optionalDouble(context, "start");
                                final double end = optionalDouble(context, "end");

                                return renderScoreForAsync(context, score, start, end);
                            })
            );
        }

        private void queueReplayRenderOfIdAsync(@NotNull Context context) {
            final String s = requireString(context, "s");

            context.future(() ->
                    router.executor.enqueueAsync(() ->
                            OsuAPI.getScore(router.tokenManager.getTokenData(), s)
                    ).thenCompose(score -> {
                        if (score == null) {
                            throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No score found for this ID!");
                        }

                        final double start = optionalDouble(context, "start");
                        final double end = optionalDouble(context, "end");

                        return renderScoreForAsync(context, score, start, end);
                    })
            );
        }

        private void getBeatmapsetOfRefAsync(@NotNull Context context) {
            context.future(() -> getScoreFromRefAsync(context)
                    .thenCompose(score -> {
                        if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);

                        return router.executor.enqueueAsync(() -> OsuAPI.getBeatmapset(router.tokenManager.getTokenData(), String.valueOf(score.getBeatmapset().getId())));
                    })
                    .thenApplyAsync(beatmapset -> {
                        if (beatmapset == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                        return router.renderer.renderBeatmapset(beatmapset);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getBeatmapsetOfMapAsync(@NotNull Context context) {
            final String m = requireNumberString(context, "m");

            context.future(() -> router.executor.enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(router.tokenManager.getTokenData(), m))
                    .thenApplyAsync(ms -> {
                        if (ms == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                        return router.renderer.renderBeatmapset(ms);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getBeatmapsetOfIdAsync(@NotNull Context context) {
            final String ms = requireNumberString(context, "ms");

            context.future(() -> router.executor.enqueueAsync(() -> OsuAPI.getBeatmapset(router.tokenManager.getTokenData(), ms))
                    .thenApplyAsync(beatmapset -> {
                        if (beatmapset == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                        return router.renderer.renderBeatmapset(beatmapset);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getBeatmapOfSetAsync(@NotNull Context context) {
            final String ms = requireNumberString(context, "ms");
            final int i = requireInt(context, "i");
            final String mod = optionalString(context, "mod");

            context.future(() -> router.executor
                    .enqueueAsync(() -> OsuAPI.getBeatmapset(router.tokenManager.getTokenData(), ms))
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
                        return router.renderer.renderBeatmap(beatmapExtended, spec);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getBeatmapOfIdAsync(@NotNull Context context) {
            final String m = requireNumberString(context, "m");
            final String mod = optionalString(context, "mod");

            context.future(() -> router.executor
                    .enqueueAsync(() -> OsuAPI.getBeatmapsetFromBeatmap(router.tokenManager.getTokenData(), m))
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
                        return router.renderer.renderBeatmap(beatmap, diffSpec);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getBeatmapOfRefAsync(@NotNull Context context) {
            final String of = requireStringFrom(context, "of", "rs", "bo");
            final int i = requireInt(context, "i");
            final String u = requireNumberString(context, "u");

            context.future(() -> router.executor.enqueueAsync(() -> OsuAPI.getUserScores(router.tokenManager.getTokenData(), u, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, i, false))
                    .thenCompose(scores -> {
                        if (scores == null || scores.isEmpty())
                            throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found");
                        final var beatmapId = scores.getLast().getBeatmap().getId();
                        final var beatmapsetId = scores.getLast().getBeatmapset().getId();
                        return router.executor
                                .enqueueAsync(() -> OsuAPI.getBeatmapset(router.tokenManager.getTokenData(), String.valueOf(beatmapsetId)))
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
                                    return router.renderer.renderBeatmap(beatmap, diffSpec);
                                }, router.renderer.getRenderExecutor());
                    })
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private byte[] getPKFinalBytes(LinkedList<Placement> placements, BeatmapExtended beatmap, String rosuBeatmapPath) {
            try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(rosuBeatmapPath);
                 final RosuFFI.Performance perfSS = new RosuFFI.Performance()
            ) {
                perfSS.setAccuracy(100.0);
                perfSS.setMisses(0);
                perfSS.setCombo(beatmap.getMaxCombo());

                return router.renderer.renderPK(beatmap, placements, perfSS.calculate(rosuBeatmap).osu.t.pp);
            } catch (RosuFFI.FFIException e) {
                throw new ApiException(ErrorCode.ROSU_ERROR, "Failed to calculate difficulty with RosuFFI: " + e.getMessage());
            }
        }

        private void getPKOfRefAsync(@NotNull Context context) {
            final String of = requireStringFrom(context, "of", "rs", "bo");
            final int i = requireInt(context, "i");
            final String uSource = requireString(context, "us");
            final String[] u = Arrays.stream(requireString(context, "u").split(",")).distinct().toArray(String[]::new);

            context.future(() -> router.executor.enqueueAsync(() -> OsuAPI.getUserScores(router.tokenManager.getTokenData(), uSource, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, i, false))
                    .thenCompose(scores -> {
                        if (scores == null || scores.size() < i) throw new ApiException(ErrorCode.NO_SCORE_FOUND);

                        final Long id = scores.get(i - 1).getBeatmap().getId();
                        return getPlacementsAsync(u, String.valueOf(id));
                    })
                    .thenApply(placements -> {
                        placements.sort((a, b) -> Double.compare(b.score.pp, a.score.pp));
                        return placements;
                    })
                    .thenCompose(placements -> router.executor.enqueueAsync(() -> OsuAPI.getBeatmap(router.tokenManager.getTokenData(), String.valueOf(placements.getFirst().score.getBeatmap().getId())))
                            .thenApplyAsync(beatmap -> {
                                if (beatmap == null) throw new ApiException(ErrorCode.NO_BEATMAP_FOUND);
                                final String rosuBeatmapPath = router.cacheService.getRosuBeatmapPath(String.valueOf(beatmap.getId()), false);

                                return getPKFinalBytes(placements, beatmap, rosuBeatmapPath);
                            }, router.renderer.getRenderExecutor()))
                    .thenAccept(imgByte -> context.status(200).result(imgByte)));
        }

        private CompletableFuture<LinkedList<Placement>> getPlacementsAsync(String[] u, String id) {
            List<CompletableFuture<Placement>> placementFutures = Arrays.stream(u).map(s ->
                    router.executor
                            .enqueueAsync(() ->
                                    OsuAPI.getUserScore(router.tokenManager.getTokenData(), s, id)
                            )
                            .thenCompose(score -> {
                                if (score == null || score.getPp() == null) {
                                    return CompletableFuture.completedFuture(null);
                                }
                                return router.executor
                                        .enqueueAsync(() ->
                                                OsuAPI.getUser(router.tokenManager.getTokenData(), s)
                                        )
                                        .thenApply(user -> {
                                            if (user == null) return null;

                                            final Placement placement = new Placement();
                                            placement.setScore(score);
                                            placement.setUser(user);
                                            return placement;
                                        });
                            })).toList();

            CompletableFuture<?>[] futuresArray = placementFutures.toArray(new CompletableFuture[0]);

            return CompletableFuture.allOf(futuresArray)
                    .thenApply(_ ->
                            placementFutures
                                    .stream()
                                    .map(CompletableFuture::join)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toCollection(LinkedList::new)));
        }

        private void getPKOfIdsAsync(@NotNull Context context) {
            final String m = requireNumberString(context, "m");
            final String us = requireString(context, "u");
            final String[] u = Arrays.stream(us.split(",")).distinct().toArray(String[]::new);

            context.future(() -> getPlacementsAsync(u, m)
                    .thenApply(p -> {
                        p.sort((a, b) -> Double.compare(b.score.pp, a.score.pp));
                        return p;
                    })
                    .thenCompose(placements ->
                            router.executor.enqueueAsync(() -> OsuAPI.getBeatmap(router.tokenManager.getTokenData(), m))
                                    .thenApplyAsync(beatmap -> {
                                        if (beatmap == null) throw new ApiException(ErrorCode.NO_BEATMAP_FOUND);
                                        final String rosuBeatmapPath = router.cacheService.getRosuBeatmapPath(m, false);

                                        return getPKFinalBytes(placements, beatmap, rosuBeatmapPath);
                                    }, router.renderer.getRenderExecutor()))
                    .thenAccept(imgByte -> context.status(200).result(imgByte)));
        }

        private void renderShowcaseOfIdsAsync(@NotNull Context context) {
            if (router.replayService == null) return;

            final String ss = requireString(context, "s");
            final List<String> scoreIds = Arrays.stream(ss.split(",")).distinct().toList();

            context.future(() -> {
                List<CompletableFuture<Score>> scoreFutures = scoreIds.stream()
                        .map(id -> router.executor.enqueueAsync(() ->
                                OsuAPI.getScore(router.tokenManager.getTokenData(), id)) //
                        ).toList();

                return CompletableFuture.allOf(scoreFutures.toArray(new CompletableFuture[0]))
                        .thenApply(_ -> scoreFutures.stream()
                                .map(CompletableFuture::join) // Safe because allOf is finished
                                .filter(s -> s != null && s.getPp() != null && s.getHasReplay())
                                .collect(Collectors.toCollection(LinkedList::new))
                        )
                        .thenCompose(validScores -> {
                            final double start = optionalDouble(context, "start");
                            final double end = optionalDouble(context, "end");

                            return renderShowcaseForAsync(context, validScores, start, end);
                        });
            });
        }

        private void renderShowcaseOfUsersAsync(@NotNull Context context) {
            if (router.replayService == null) return;

            final String us = requireString(context, "u");
            final String m = requireString(context, "m");

            context.future(() -> {
                final List<String> userIds = Arrays.stream(us.split(",")).distinct().toList();
                LOG.info("Getting {} scores for showcase on map {}", userIds.size(), m);

                List<CompletableFuture<Score>> scoreFutures = userIds.stream()
                        .map(userId -> router.executor.enqueueAsync(() ->
                                OsuAPI.getUserScore(router.tokenManager.getTokenData(), userId, m))
                        ).toList();

                return CompletableFuture.allOf(scoreFutures.toArray(new CompletableFuture[0]))
                        .thenApply(_ -> scoreFutures.stream()
                                .map(CompletableFuture::join)
                                .filter(s -> s != null && s.getPp() != null && s.getHasReplay())
                                .collect(Collectors.toCollection(LinkedList::new))
                        )
                        .thenCompose(validScores -> {
                            final double start = optionalDouble(context, "start");
                            final double end = optionalDouble(context, "end");

                            return renderShowcaseForAsync(context, validScores, start, end);
                        });
            });
        }

        private void renderShowcaseOfUsersRefAsync(@NotNull Context context) {
            final String us = requireString(context, "u");
            final String of = requireStringFrom(context, "of", "rs", "bo");
            final String uSource = requireString(context, "us");
            final int i = requireInt(context, "i");

            context.future(() ->
                    router.executor.enqueueAsync(() ->
                            OsuAPI.getUserScores(router.tokenManager.getTokenData(), uSource,
                                    of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, i, false)
                    ).thenCompose(scores -> {
                        if (scores == null || scores.size() < i) {
                            throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found for user!");
                        }

                        final Score scoreSource = scores.get(i - 1);
                        final String beatmapId = String.valueOf(scoreSource.getBeatmap().getId());
                        final List<String> userIds = Arrays.stream(us.split(",")).distinct().toList();

                        List<CompletableFuture<Score>> scoreFutures = userIds.stream()
                                .map(userId -> router.executor.enqueueAsync(() ->
                                        OsuAPI.getUserScore(router.tokenManager.getTokenData(), userId, beatmapId))
                                ).toList();

                        return CompletableFuture.allOf(scoreFutures.toArray(new CompletableFuture[0]))
                                .thenApply(_ -> scoreFutures.stream()
                                        .map(CompletableFuture::join)
                                        .filter(s -> s != null && s.getPp() != null && s.getHasReplay())
                                        .collect(Collectors.toCollection(LinkedList::new))
                                );
                    }).thenCompose(validScores -> {
                        final double start = optionalDouble(context, "start");
                        final double end = optionalDouble(context, "end");

                        return renderShowcaseForAsync(context, validScores, start, end);
                    })
            );
        }

        private CompletableFuture<Void> renderShowcaseForAsync(@NotNull Context context, LinkedList<Score> scores, Double start, Double end) {
            if (router.replayService == null) return CompletableFuture.completedFuture(null);

            if (scores.isEmpty()) {
                throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No valid scores found!");
            }

            final long beatmapId = scores.getFirst().getBeatmap().getId();
            final long beatmapsetId = scores.getFirst().getBeatmapset().getId();

            CompletableFuture<Boolean> cacheFuture = router.executor.enqueueAsync(() ->
                    router.cacheService.cacheBeatmapset(String.valueOf(beatmapsetId)));

            CompletableFuture<BeatmapExtended> beatmapFuture = router.executor.enqueueAsync(() ->
                    OsuAPI.getBeatmap(router.tokenManager.getTokenData(), String.valueOf(beatmapId)));

            return CompletableFuture.allOf(cacheFuture, beatmapFuture).thenCompose(_ -> {
                if (!cacheFuture.join())
                    throw new ApiException(ErrorCode.BEATMAPSET_FETCH_FAILED, "Failed to cache beatmapset!");
                BeatmapExtended beatmap = beatmapFuture.join();
                if (beatmap == null) throw new ApiException(ErrorCode.BEATMAP_FETCH_FAILED, "Failed to get beatmap!");

                List<CompletableFuture<Path>> replayFutures = scores.stream()
                        .map(score -> router.executor.enqueueAsync(() -> {
                            try {
                                return router.cacheService.getReplay(router.tokenManager.getTokenData(), String.valueOf(score.getId()));
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

                            final int queueSize = router.replayService.getQueueSize() + 1;
                            if (queueSize > router.conf.replayRender().renderQueueSize()) {
                                throw new ApiException(ErrorCode.RENDER_QUEUE_FULL, "Render queue full!");
                            }

                            final String jobId = router.replayService.queueRenderShowcase(String.valueOf(beatmapId), replays, start, end);

                            context.status(202).result(new Response(true, "Replay render queued!",
                                    router.GSON.toJsonTree(Map.of(
                                            "status", "queued",
                                            "position", queueSize,
                                            "id", jobId,
                                            "beatmap", Map.of(
                                                    "title", beatmap.getBeatmapset().getTitle(),
                                                    "artist", beatmap.getBeatmapset().getArtist(),
                                                    "version", beatmap.getVersion(),
                                                    "star", String.format("%.2f★", beatmap.getDifficultyRating())
                                            ),
                                            "scores", getScoresArr(scores)
                                    ))).toString());
                        });
            });
        }

        private JsonArray getScoresArr(List<Score> scores) {
            JsonArray scoresArr = new JsonArray();
            for (Score score : scores) {
                JsonObject scoreObj = new JsonObject();
                scoreObj.addProperty("username", score.getUser().getUsername());
                scoreObj.addProperty("rank", score.getRank());
                scoreObj.addProperty("accuracy", String.format("%.2f%%", score.getAccuracy() * 100));
                scoreObj.addProperty("pp", String.format("%.2fpp", score.getPp()));
                scoresArr.add(scoreObj);
            }
            return scoresArr;
        }

        private void getScoreOfIdAsync(@NotNull Context context) {
            final String s = requireNumberString(context, "s");
            context.future(() -> router.executor.enqueueAsync(() -> OsuAPI.getScore(router.tokenManager.getTokenData(), s))
                    .thenApplyAsync(score -> {
                        if (score == null) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                        final BeatmapExtended beatmap = score.getBeatmap();

                        final DiffSpec diffSpec = getDiffSpecForMap(beatmap, router.getRosuPath(beatmap.getId()), score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));

                        return router.renderer.renderScore(score, diffSpec);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getScoreOfBeatmapAsync(@NotNull Context context) {
            final String m = requireNumberString(context, "m");
            final String u = requireNumberString(context, "u");

            context.future(() -> router.executor.enqueueAsync(() -> OsuAPI.getUserScore(router.tokenManager.getTokenData(), u, m))
                    .thenCompose(score ->
                            router.executor
                                    .enqueueAsync(() -> OsuAPI.getBeatmapset(router.tokenManager.getTokenData(), String.valueOf(score.getBeatmap().getBeatmapsetId())))
                                    .thenApply(beatmapset -> {
                                        score.getBeatmap().setBeatmapset(beatmapset);
                                        score.setBeatmapset(beatmapset);
                                        return score;
                                    })
                    )
                    .thenApplyAsync(score -> {
                        final DiffSpec diffSpec = getDiffSpecForMap(
                                score.getBeatmap(), router.getRosuPath(score.getBeatmap().getId()),
                                score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat)
                        );

                        return router.renderer.renderScore(score, diffSpec);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getScoreOfRefAsync(@NotNull Context context) {
            context.future(() -> getScoreFromRefAsync(context)
                    .thenApply(Score::getId)
                    .thenCompose(scoreId -> router.executor.enqueueAsync(() -> OsuAPI.getScore(router.tokenManager.getTokenData(), String.valueOf(scoreId))))
                    .thenApplyAsync(score -> {
                        final DiffSpec diffSpec = getDiffSpecForMap(score.getBeatmap(), router.getRosuPath(score.getBeatmap().getId()), score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));
                        return router.renderer.renderScore(score, diffSpec);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private void getScoreOfBeatmapsetAsync(@NotNull Context context) {
            context.future(() -> getScoreFromBeatmapsetAsync(context)
                    .thenApplyAsync(score -> {
                        final DiffSpec diffSpec = getDiffSpecForMap(
                                score.getBeatmap(), router.getRosuPath(score.getBeatmap().getId()),
                                score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat)
                        );
                        return router.renderer.renderScore(score, diffSpec);
                    }, router.renderer.getRenderExecutor())
                    .thenAccept(bytes -> context.status(200).result(bytes)));
        }

        private CompletableFuture<Score> getScoreFromBeatmapsetAsync(@NotNull Context context) {
            final String ms = requireNumberString(context, "ms");
            final int i = requireInt(context, "i");
            final String u = requireNumberString(context, "u");

            return router.executor.enqueueAsync(() -> OsuAPI.getBeatmapset(router.tokenManager.getTokenData(), ms))
                    .thenCompose(beatmapset -> {
                        if (beatmapset == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);

                        final List<BeatmapExtended> beatmaps = beatmapset.getBeatmaps();

                        if (beatmaps.size() < i) throw new ApiException(ErrorCode.NO_BEATMAP_FOUND);

                        beatmaps.sort(Comparator.comparingDouble(BeatmapExtended::getDifficultyRating));
                        final BeatmapExtended beatmap = beatmaps.get(i - 1);
                        beatmap.setBeatmapset(beatmapset);
                        return router.executor
                                .enqueueAsync(() ->
                                        OsuAPI.getUserScore(router.tokenManager.getTokenData(), u, String.valueOf(beatmap.getId()))
                                )
                                .thenApply(score -> {
                                    score.setBeatmap(beatmap);
                                    score.setBeatmapset(beatmapset);
                                    return score;
                                });
                    });

        }

        private CompletableFuture<Score> getScoreFromRefAsync(@NotNull Context context) {
            final String of = requireStringFrom(context, "of", "rs", "bo");
            final String u = requireString(context, "u");
            final int i = requireInt(context, "i");

            return router.executor
                    .enqueueAsync(() -> OsuAPI.getUserScores(router.tokenManager.getTokenData(), u, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, i, false))
                    .thenApply(scores -> {
                        if (scores.isEmpty() || scores.size() < i) {
                            throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found for user!");
                        }

                        return scores.get(i - 1);
                    });
        }

        private CompletableFuture<Void> renderScoreForAsync(@NotNull Context context, Score score, Double start, Double end) {
            if (router.replayService == null) return CompletableFuture.completedFuture(null);

            if (!score.getHasReplay()) {
                throw new ApiException(ErrorCode.REPLAY_UNAVAILABLE, "Replay unavailable!");
            }

            return router.executor.enqueueAsync(() -> {
                if (!router.cacheService.cacheBeatmapset(String.valueOf(score.getBeatmapset().getId()))) {
                    throw new ApiException(ErrorCode.BEATMAPSET_FETCH_FAILED, "Failed to cache beatmapset!");
                }
                return null;
            }).thenCompose(_ ->
                    router.executor.enqueueAsync(() -> {
                        try {
                            return router.cacheService.getReplay(router.tokenManager.getTokenData(), String.valueOf(score.getId()));
                        } catch (Exception e) {
                            throw new ApiException(ErrorCode.REPLAY_FETCH_FAILED, "Failed to cache replay for score id: " + score.getId(), e);
                        }
                    })
            ).thenAccept(replayPath -> {
                final int queueSize = router.replayService.getQueueSize() + 1;

                if (queueSize > router.conf.replayRender().renderQueueSize()) {
                    throw new ApiException(ErrorCode.RENDER_QUEUE_FULL, "Replay rendering queue is full!");
                }

                final String jobId = router.replayService.queueRender(replayPath, start, end);

                context.status(202).result(
                        new Response(
                                true,
                                "Replay render queued!",
                                router.GSON.toJsonTree(Map.of(
                                        "status", "queued",
                                        "position", queueSize,
                                        "id", jobId,
                                        "beatmap", Map.of(
                                                "title", score.getBeatmapset().getTitle(),
                                                "artist", score.getBeatmapset().getArtist(),
                                                "version", score.getBeatmap().getVersion(),
                                                "star", String.format("%.2f★", score.getBeatmap().getDifficultyRating())
                                        ),
                                        "scores", getScoresArr(List.of(score))
                                ))
                        ).toString()
                );
            });
        }
    }
}
