package xyz.zcraft.ostella.network.controller;

import com.google.gson.Gson;
import io.javalin.http.Context;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.ostella.data.Placement;
import xyz.zcraft.ostella.network.ApiException;
import xyz.zcraft.ostella.network.ErrorCode;
import xyz.zcraft.ostella.network.OsuAPI;
import xyz.zcraft.ostella.network.Router;
import xyz.zcraft.ostella.service.AsyncService;
import xyz.zcraft.ostella.service.CacheService;
import xyz.zcraft.ostella.service.RenderService;
import xyz.zcraft.ostella.util.TokenManager;
import xyz.zcraft.osu.model.BeatmapExtended;
import xyz.zcraft.osu.model.User;
import xyz.zcraft.osu.parser.OsuParser;
import xyz.zcraft.osu.parser.data.beatmap.DiffSpec;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static xyz.zcraft.ostella.util.RequestUtil.requirePathLong;

public class LeaderboardController {
    private static final Gson GSON = new Gson();

    public final RenderService renderer;
    public final AsyncService executor;
    public final TokenManager tokenManager;
    public final Router router;

    public LeaderboardController(Router router) {
        this.router = router;
        this.renderer = router.renderer;
        this.tokenManager = router.tokenManager;
        this.executor = router.executor;
    }

    public void getMapLeaderboard(@NotNull Context context) {
        final long m = requirePathLong(context, "beatmapId");
        final LeaderboardRequest leaderboardRequest = GSON.fromJson(context.body(), LeaderboardRequest.class);

        context.header("X-Beatmap-Id", String.valueOf(m));

        context.future(() -> getPlacementsAsync(leaderboardRequest.uids(), m)
                .thenApply(p -> {
                    p.sort((a, b) -> Long.compare(b.getScore().getTotalScore(), a.getScore().getTotalScore()));
                    return p;
                })
                .thenCompose(placements ->
                        executor.enqueueAsync(() -> OsuAPI.getBeatmap(tokenManager.getTokenData(), m))
                                .thenApplyAsync(beatmap -> {
                                    if (beatmap == null) throw new ApiException(ErrorCode.NO_BEATMAP_FOUND);
                                    final Path rosuBeatmapPath = CacheService.getRosuBeatmapPath(m, false);

                                    return finalizeMapLeaderboard(placements, beatmap, rosuBeatmapPath);
                                }, renderer.getRenderExecutor()))
                .thenAccept(imgByte -> context.status(200).result(imgByte)));
    }

    private byte[] finalizeMapLeaderboard(LinkedList<Placement> placements, BeatmapExtended beatmap, Path rosuBeatmapPath) {
        try {
            final DiffSpec diffSpecForMap = OsuParser.getDiffSpecForMap(beatmap, rosuBeatmapPath, "");
            return renderer.renderMapLeaderboard(beatmap, placements, diffSpecForMap.getPpSS());
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.ROSU_ERROR, "Failed to calculate difficulty with RosuFFI: " + e.getMessage());
        }
    }

    private CompletableFuture<LinkedList<Placement>> getPlacementsAsync(List<Long> u, long id) {
        List<CompletableFuture<Placement>> placementFutures = u.stream().map(s ->
                executor
                        .enqueueAsync(() ->
                                OsuAPI.getUserScore(tokenManager.getTokenData(), s, id)
                        )
                        .thenCompose(score -> {
                            if (score == null) {
                                return CompletableFuture.completedFuture(null);
                            }

                            if (score.getPp() == null) {
                                score.setPp(OsuParser.estimatePp(score, CacheService.getRosuBeatmapPath(id, false)));
                            }

                            return executor
                                    .enqueueAsync(() ->
                                            OsuAPI.getUser(tokenManager.getTokenData(), s)
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

    public void getLeaderboard(@NotNull Context context) {
        final LeaderboardRequest leaderboardRequest = GSON.fromJson(context.body(), LeaderboardRequest.class);
        final List<Long> ids = leaderboardRequest.uids();

        List<CompletableFuture<List<User>>> futures = new ArrayList<>();

        for (int i = 0; i < ids.size(); i += 50) {
            final List<Long> subList = ids.subList(i, Math.min(i + 50, ids.size()));

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
                                            .map(User.StatisticsRuleset::getOsu)
                                            .map(User.Statistics::getPp)
                                            .orElse(0.0)
                            ).reversed());
                            return renderer.renderLeaderboard(users);

                        }, renderer.getRenderExecutor())
                        .thenAccept(imgByte -> context.status(200).result(imgByte))
        );
    }

    public record LeaderboardRequest(List<Long> uids) {
    }
}
