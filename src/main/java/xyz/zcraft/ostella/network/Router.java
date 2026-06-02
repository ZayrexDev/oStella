package xyz.zcraft.ostella.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.ostella.config.AppConfig;
import xyz.zcraft.ostella.data.ScoreType;
import xyz.zcraft.ostella.data.SearchResultItem;
import xyz.zcraft.ostella.network.controller.*;
import xyz.zcraft.ostella.service.AsyncService;
import xyz.zcraft.ostella.service.CacheService;
import xyz.zcraft.ostella.service.RenderService;
import xyz.zcraft.ostella.service.ReplayService;
import xyz.zcraft.ostella.util.TokenManager;
import xyz.zcraft.osu.model.BeatmapExtended;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.model.MultiplayerRoom;
import xyz.zcraft.osu.model.Score;
import xyz.zcraft.osu.parser.OsuParser;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static xyz.zcraft.ostella.util.RequestUtil.*;

public class Router implements Closeable {
    static final Logger LOG = LogManager.getLogger(Router.class);
    public final RenderService renderer;
    public final AsyncService executor;
    public final TokenManager tokenManager;
    public final ReplayService replayService;
    public final AppConfig conf;
    final Gson GSON = new Gson();
    final ReplayController replayController;
    final BeatmapController beatmapController;
    final ScoreController scoreController;
    final BeatmapsetController beatmapsetController;
    final LeaderboardController leaderboardController;
    final AnalyzeController analyzeController;

    public Router(AppConfig conf, TokenManager tokenManager) throws IOException {
        this.conf = conf;
        this.tokenManager = tokenManager;
        this.executor = new AsyncService(conf.ostella().requestPerSecond());

        CacheService.initialize(this.executor);

        this.renderer = new RenderService(conf.ostella().renderWorkers());

        this.beatmapController = new BeatmapController(this);
        this.scoreController = new ScoreController(this);
        this.beatmapsetController = new BeatmapsetController(this);
        this.leaderboardController = new LeaderboardController(this);
        this.analyzeController = new AnalyzeController(this);

        if (conf.replayRender().enabled()) {
            this.replayService = new ReplayService(conf, CacheService.getDanserCache());
            this.replayController = new ReplayController(this);
        } else {
            this.replayService = null;
            this.replayController = null;
        }

        LOG.info("Router created");
    }

    protected void searchBeatmapSet(@NotNull Context context) {
        final String query = requireString(context, "q");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.searchBeatmapset(tokenManager.getTokenData(), query))
                .thenApply(result -> {
                    if (result == null || result.isEmpty()) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);
                    final List<SearchResultItem> list = result.stream().map(SearchResultItem::fromBeatmapset).toList();
                    final var ids = list.stream().map(SearchResultItem::beatmapsetId).map(String::valueOf).toList();
                    context.header("X-Beatmapset-Ids", String.join(",", ids));
                    return list;
                })
                .thenAccept(
                        result -> context.status(200).result(
                                new Response(true, "Query successful", GSON.toJsonTree(result)).toString()
                        )
                ));
    }

    protected void getServerStatus(@NotNull Context context) {
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

    protected void getFriends(@NotNull Context context) {
        final String auth = context.header("Authorization");

        if (auth == null) {
            context.status(401)
                    .result(Response.error("Missing Authorization header", ErrorCode.UNAUTHORIZED).toString());
            return;
        }

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getFriends(auth))
                .thenApply(r -> {
                    if (r == null)
                        throw new ApiException(ErrorCode.NO_USER_FOUND, "No user found for the provided token!");
                    JsonArray arr = new JsonArray();
                    r.forEach(ur -> {
                        JsonObject obj = new JsonObject();
                        obj.add("user", GSON.toJsonTree(ur.target()));
                        obj.addProperty("mutual", ur.mutual());
                        arr.add(obj);
                    });
                    return arr;
                })
                .thenAccept(arr -> context.status(200).result(new Response(true, "Success", arr).toString()))
        );
    }

    protected void getSelf(@NotNull Context context) {
        final String auth = context.header("Authorization");

        if (auth == null) {
            context.status(401)
                    .result(Response.error("Missing Authorization header", ErrorCode.UNAUTHORIZED).toString());
            return;
        }

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getSelf(auth))
                .thenApply(u -> {
                    if (u == null)
                        throw new ApiException(ErrorCode.NO_USER_FOUND, "No user found for the provided token!");
                    return u;
                })
                .thenCompose(u -> executor.enqueueAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), u.getId())))
                .thenAccept(u -> context.status(200).result(new Response(true, "Success", GSON.toJsonTree(u)).toString()))
        );
    }

    protected void getRecentScores(@NotNull Context context) {
        final long u = requirePathLong(context, "userId");
        final int n = requireInt(context, "n");
        final boolean fail = requireBoolean(context, "fail", false);

        final ScoreType type = fail ? ScoreType.RECENT : ScoreType.RECENT_PASS;

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getUserScores(
                        tokenManager.getTokenData(), u, type, n)
                )
                .thenCompose(scores -> executor.enqueueAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), u))
                        .thenApplyAsync(user -> {
                            if (user == null) {
                                throw new ApiException(ErrorCode.NO_USER_FOUND, "No user found");
                            }
                            context.header("X-User-Id", String.valueOf(user.getId()));
                            context.header("X-Score-Ids", scores.stream().map(Score::getId).map(String::valueOf).collect(Collectors.joining(",")));

                            for (Score score : scores) {
                                if (score.getPp() == null) {
                                    score.setPp(OsuParser.estimatePp(score, getRosuPath(score.getBeatmap().getId())));
                                }
                            }

                            return renderer.renderScores(user, scores, fail ? ScoreType.RECENT : ScoreType.RECENT_PASS);
                        }, renderer.getRenderExecutor()))
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    protected void getDaily(@NotNull Context context) {
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

    protected void getBestOfN(@NotNull Context context) {
        final long u = requirePathLong(context, "userId");
        final int n = requireInt(context, "n");

        context.future(() -> executor.enqueueAsync(() -> OsuAPI.getUserScores(
                        tokenManager.getTokenData(), u, ScoreType.BEST, n
                ))
                .thenCompose(scores -> {
                    if (scores == null || scores.isEmpty()) throw new ApiException(ErrorCode.NO_SCORE_FOUND);
                    return executor.enqueueAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), u))
                            .thenApplyAsync(user -> {
                                if (user == null) throw new ApiException(ErrorCode.NO_USER_FOUND);
                                context.header("X-User-Id", String.valueOf(user.getId()));
                                context.header("X-Score-Ids", scores.stream().map(Score::getId).map(String::valueOf).collect(Collectors.joining(",")));
                                return renderer.renderScores(user, scores, ScoreType.BEST);
                            }, renderer.getRenderExecutor());
                })
                .thenAccept(bytes -> context.status(200).result(bytes)));
    }

    public Path getRosuPath(Long id) {
        return CacheService.getRosuBeatmapPath(id, true);
    }

    @Override
    public void close() {
        executor.close();
        renderer.close();
        replayService.close();
    }

    public JsonArray getScoresArr(List<Score> scores) {
        JsonArray scoresArr = new JsonArray();
        for (Score score : scores) {
            JsonObject scoreObj = new JsonObject();
            scoreObj.addProperty("username", score.getUser().getUsername());
            scoreObj.addProperty("rank", score.getRank());
            scoreObj.addProperty("accuracy", String.format("%.2f%%", score.getAccuracy() * 100));
            scoreObj.addProperty("pp", String.format("%.2fpp", score.getPp()));
            scoreObj.addProperty("id", String.valueOf(score.getId()));
            scoresArr.add(scoreObj);
        }
        return scoresArr;
    }

    public CompletableFuture<Score> getScoreFromBeatmapsetAsync(@NotNull Context context) {
        final long ms = requireLong(context, "ms");
        final int i = requireInt(context, "i");
        final long u = requireLong(context, "u");

        return executor.enqueueAsync(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), ms))
                .thenCompose(beatmapset -> {
                    if (beatmapset == null) throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND);

                    final List<BeatmapExtended> beatmaps = beatmapset.getBeatmaps();

                    if (beatmaps.size() < i) throw new ApiException(ErrorCode.NO_BEATMAP_FOUND);

                    beatmaps.sort(Comparator.comparingDouble(BeatmapExtended::getDifficultyRating));
                    final BeatmapExtended beatmap = beatmaps.get(i - 1);
                    beatmap.setBeatmapset(beatmapset);
                    context.header("X-Beatmap-Id", String.valueOf(beatmap.getId()));
                    return executor
                            .enqueueAsync(() ->
                                    OsuAPI.getUserScore(tokenManager.getTokenData(), u, beatmap.getId())
                            )
                            .thenApply(score -> {
                                score.setBeatmap(beatmap);
                                score.setBeatmapset(beatmapset);
                                return score;
                            });
                });

    }

    public CompletableFuture<Score> getScoreFromRefAsync(@NotNull Context context) {
        final String of = requireStringFrom(context, "of", "rs", "bo", "rp");
        final long u = requireLong(context, "u");
        final int i = requireInt(context, "i");

        final ScoreType type = switch (of.toLowerCase()) {
            case "rs" -> ScoreType.RECENT;
            case "rp" -> ScoreType.RECENT_PASS;
            case "bo" -> ScoreType.BEST;
            default -> throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Invalid score type: " + of);
        };

        return executor
                .enqueueAsync(() -> OsuAPI.getUserScores(tokenManager.getTokenData(), u, type, i))
                .thenApply(scores -> {
                    if (scores.isEmpty() || scores.size() < i) {
                        throw new ApiException(ErrorCode.NO_SCORE_FOUND, "No scores found for user!");
                    }

                    return scores.get(i - 1);
                });
    }

    public void getCurrentRoom(@NotNull Context context) {
        final String auth = context.header("Authorization");

        if (auth == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getCurrentRoom(auth))
                .thenApply(room -> {
                    if (room == null) {
                        throw new ApiException(ErrorCode.NO_ROOM_FOUND, "User is not in a room!");
                    }
                    return room;
                })
                .thenAccept(room -> context.status(200).result(new Response(true, "Success", GSON.toJsonTree(room)).toString()))
        );
    }

    public void getCurrentRoomItem(@NotNull Context context) {
        final String auth = context.header("Authorization");

        if (auth == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }

        context.future(() -> executor
                .enqueueAsync(() -> OsuAPI.getCurrentRoom(auth))
                .thenApply(room -> {
                    if (room == null) {
                        throw new ApiException(ErrorCode.NO_ROOM_FOUND, "User is not in a room!");
                    }
                    final var currentPlaylistItem = room.getCurrentPlaylistItem();
                    if (currentPlaylistItem == null) {
                        throw new ApiException(ErrorCode.NO_BEATMAPSET_FOUND, "Room has no current playlist item!");
                    }
                    return currentPlaylistItem;
                })
                .thenApply((MultiplayerRoom.CurrentPlaylistItem c) -> {
                    final BeatmapExtended beatmap = c.getBeatmap();
                    if (beatmap == null) {
                        throw new ApiException(ErrorCode.NO_BEATMAP_FOUND, "Beatmap is null!");
                    }
                    JsonObject res = new JsonObject();
                    res.addProperty("beatmap_id", beatmap.getId());
                    res.addProperty("beatmapset_id", beatmap.getBeatmapsetId());
                    return res;
                })
                .thenAccept(obj -> context.status(200).result(new Response(true, "Success", obj).toString()))
        );
    }

    public CompletableFuture<Score> getScore(long id) {
        return executor.enqueueAsync(() -> {
            try {
                final Optional<Score> scoreJsonCache = CacheService.getScoreJsonCache(id);

                if (scoreJsonCache.isPresent()) {
                    LOG.debug("Score {} found in cache", id);
                    return scoreJsonCache.get();
                }
            } catch (Exception e) {
                LOG.warn("Failed to get score from cache for score id {}: {}", id, e.getMessage());
            }

            return OsuAPI.getScore(tokenManager.getTokenData(), id);
        });
    }
}
