package xyz.zcraft.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.nanamochi.rosu_pp_jar.Performance;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.model.*;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.score.Score;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.model.user.UserExtended;
import xyz.zcraft.service.AsyncService;
import xyz.zcraft.service.BeatmapCacheService;
import xyz.zcraft.util.Config;
import xyz.zcraft.service.ScoreRenderService;
import xyz.zcraft.util.TokenManager;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import static xyz.zcraft.util.FormatUtil.isInteger;

public class WebServer {
    private static final Logger LOG = LogManager.getLogger(WebServer.class);

    private final ScoreRenderService renderer;
    private final BeatmapCacheService cacheService;
    private final AsyncService executor;
    private final Config conf;
    private final TokenManager tokenManager;
    private final Javalin app;

    public WebServer(Config conf, TokenManager tokenManager) throws IOException {
        this.conf = conf;
        this.tokenManager = tokenManager;
        renderer = new ScoreRenderService();
        executor = new AsyncService(conf.maxThreads(), conf.delay());
        cacheService = new BeatmapCacheService();
        app = Javalin.create(cfg -> {
            cfg.routes
                    .get("bo", this::getBestOfN)
                    .get("daily", this::getDaily)
                    .get("mp", this::getMultiplayerRooms)
                    .get("rs", this::getRecentScores)
                    .get("bm", this::getBeatmap)
                    .get("status", this::getServerStatus);

            if (conf.debug()) {
                LOG.warn("/bypass endpoint is enabled in debug mode! To prevent security risks, please disable debug mode in production environment.");
                cfg.routes.get("bypass", this::bypassRequest);
            }
        });
    }

    private void getBeatmap(@NotNull Context context) {
        final String id = context.queryParam("id");

        LOG.info("{} - bm - {}", context.ip(), id);

        if (id == null || !isInteger(id)) {
            context.status(400).result("Invalid query parameter!");
            return;
        }

        contextFutureWithError(context,
                executor.runDelayAsync(() -> {
                    final BeatmapExtended beatmap = NetworkHelper.getBeatmap(tokenManager.getTokenData(), id);
                    final io.github.nanamochi.rosu_pp_jar.Beatmap rosuBeatmap = cacheService.getRosuBeatmap(id, false);

                    final Performance perfSS = Performance.create(rosuBeatmap);
                    perfSS.setAccuracy(100.0);
                    perfSS.setMisses(0);
                    perfSS.setCombo(beatmap.getMaxCombo());

                    final Performance perfFC = Performance.create(rosuBeatmap);
                    perfFC.setAccuracy(98.0);
                    perfFC.setMisses(0);
                    perfFC.setCombo(beatmap.getMaxCombo());

                    final Performance perf95 = Performance.create(rosuBeatmap);
                    perf95.setAccuracy(95.0);

                    return renderer.renderBeatmap(
                            beatmap,
                            perfSS.calculate().pp().intValue(),
                            perfFC.calculate().pp().intValue(),
                            perf95.calculate().pp().intValue()
                    );
                }).thenAccept(image -> context.status(200).contentType("img/png").result(image)),
                "Beatmap request failed! Param:{id:" + id + "}"
        );
    }

    private void bypassRequest(@NotNull Context context) {
        LOG.info("{} - bypass - {}", context.ip(), context.queryString());
        contextFutureWithError(context,
                executor.runDelayAsync(() -> NetworkHelper.byPassRequest(
                        tokenManager.getTokenData(),
                        context.queryString()
                )).thenAccept(response -> context.status(200).json(new Response(true, "Success", response).toString())),
                "Failed to bypass request");
    }

    public void start() {
        app.start(conf.port());
        LOG.info("Started web server on port {}", conf.port());
    }

    public void close() {
        app.stop();
        executor.close();
    }

    private void getServerStatus(@NotNull Context context) {
        LOG.info("{} - status", context.ip());
        context.future(() -> executor.runDelayAsync(
                () -> NetworkHelper.getUser("2", tokenManager.getTokenData())
        ).thenAccept(user -> {
            if (user != null) {
                context.status(200).json(new Response(true, "Server is running, API is up.", null));
                return;
            }

            context.status(500).json(new Response(true, "API is unreachable.", null));
        }).exceptionally(e -> {
            LOG.error("Server status query failed", unwrapCompletionException(e));
            context.status(500).json(new Response(true, "API is unreachable.", null));
            return null;
        }));
    }

    private void getRecentScores(@NotNull Context ctx) {
        final String id = ctx.queryParam("id");
        final String n = ctx.queryParam("n");

        LOG.info("{} - recent - {} - {}", ctx.ip(), id, n);

        if (id == null || n == null || !isInteger(n)) {
            ctx.status(400).result("Invalid query parameter!");
            return;
        }

        contextFutureWithError(ctx,
                executor.runDelayAsync(() -> {
                    final List<Score> scores = NetworkHelper.getUserScores(
                            tokenManager.getTokenData(),
                            id,
                            ScoreType.RECENT,
                            Integer.parseInt(n),
                            true
                    );
                    final UserExtended user = NetworkHelper.getUser(id, tokenManager.getTokenData());

                    return renderer.renderScores(user, scores, ScoreType.RECENT);
                }).thenAccept(image -> ctx.status(200).contentType("img/png").result(image)),
                "Recent scores request failed! Param:{n=" + n + ", id:" + id + "}"
        );
    }

    private void getMultiplayerRooms(@NotNull Context context) {
        LOG.info("{} - mp", context.ip());

        contextFutureWithError(context,
                executor.runDelayAsync(() -> {
                    final List<MultiplayerRoom> rooms = NetworkHelper.getRooms(tokenManager.getTokenData());
                    rooms.sort(Comparator.comparingInt(MultiplayerRoom::getParticipantCount));

                    List<MultiplayerRoom> topRooms = rooms.size() > 20 ? rooms.reversed().subList(0, 20) : rooms;
                    JsonArray arr = new JsonArray();
                    for (MultiplayerRoom room : topRooms) {
                        arr.add(room.getName() + " << " + room.getParticipantCount());
                    }
                    return arr;
                }).thenAccept(arr -> context.status(200).json(new Response(true, "Success", arr).toString())),
                "Multiplayer rooms request failed!"
        );
    }

    private void getDaily(@NotNull Context context) {
        LOG.info("{} - daily", context.ip());

        contextFutureWithError(context,
                executor.runDelayAsync(() -> NetworkHelper.getRooms(tokenManager.getTokenData()).stream()
                        .filter(room -> Objects.equals(room.getCategory(), "daily_challenge"))
                        .findFirst()).thenAccept(roomResult -> {
                    if (roomResult.isEmpty()) {
                        context.status(404).result(new Response(false, "Daily challenge room not found!", null).toString());
                        return;
                    }

                    final MultiplayerRoom room = roomResult.get();
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

                    context.status(200).json(new Response(true, "Success", data).toString());
                }),
                "Daily challenge request failed!"
        );
    }

    private void getBestOfN(@NotNull Context ctx) {
        final String id = ctx.queryParam("id");
        final String n = ctx.queryParam("n");

        LOG.info("{} - boN - {} - {}", ctx.ip(), id, n);

        if (id == null || n == null || !isInteger(n)) {
            ctx.status(400)
                    .result("Invalid query parameter!");
            return;
        }

        contextFutureWithError(ctx,
                executor.runDelayAsync(() -> {
                    final List<Score> scores = NetworkHelper.getUserScores(
                            tokenManager.getTokenData(),
                            id,
                            ScoreType.BEST,
                            Integer.parseInt(n),
                            false
                    );
                    final UserExtended user = NetworkHelper.getUser(id, tokenManager.getTokenData());

                    return renderer.renderScores(user, scores, ScoreType.BEST);
                }).thenAccept(image -> ctx.status(200).contentType("img/png").result(image)),
                "Best of N request failed! Param:{n=" + n + ", id:" + id + "}"
        );
    }

    private void contextFutureWithError(@NotNull Context ctx, java.util.concurrent.CompletableFuture<Void> future, String logMessage) {
        ctx.future(() -> future.exceptionally(e -> {
            Throwable cause = unwrapCompletionException(e);
            ctx.status(500).result(new Response(false, cause.getMessage(), null).toString());
            LOG.error(logMessage, cause);
            return null;
        }));
    }

    private Throwable unwrapCompletionException(Throwable e) {
        if (e instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return e;
    }
}
