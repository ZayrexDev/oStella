package xyz.zcraft.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.data.*;
import xyz.zcraft.util.AsyncTaskExecutor;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.ScoreRenderer;
import xyz.zcraft.util.TokenManager;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import static xyz.zcraft.util.FormatUtil.isInteger;

public class WebServer {
    private static final Logger LOG = LogManager.getLogger(WebServer.class);

    private final ScoreRenderer renderer;
    private final AsyncTaskExecutor executor;
    private final Config conf;
    private final int delayMillis;
    private final TokenManager tokenManager;
    private final Javalin app;

    public WebServer(Config conf, TokenManager tokenManager) {
        this.conf = conf;
        this.delayMillis = conf.delay();
        this.tokenManager = tokenManager;
        renderer = new ScoreRenderer();
        executor = new AsyncTaskExecutor(conf.maxThreads());
        app = Javalin.create(cfg -> {
            cfg.routes
                    .get("bo", this::getBestOfN)
                    .get("daily", this::getDaily)
                    .get("mp", this::getMultiplayerRooms)
                    .get("rs", this::getRecentScores)
                    .get("status", this::getServerStatus);

            if (conf.debug()) {
                LOG.warn("/bypass endpoint is enabled in debug mode! To prevent security risks, please disable debug mode in production environment.");
                cfg.routes.get("bypass", this::bypassRequest);
            }
        });
    }

    private void bypassRequest(@NotNull Context context) {
        LOG.info("{} - bypass - {}", context.ip(), context.queryString());
        contextFutureWithError(context,
                executor.runAsync(() -> NetworkHelper.byPassRequest(
                        tokenManager.getTokenData(),
                        context.queryString()
                ), delayMillis).thenAccept(response -> context.status(200).json(new Response(true, "Success", response).toString())),
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
        context.future(() -> executor.runAsync(
                () -> NetworkHelper.getUser("2", tokenManager.getTokenData()),
                delayMillis
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
                executor.runAsync(() -> {
                    final List<Score> scores = NetworkHelper.getUserScores(
                            tokenManager.getTokenData(),
                            id,
                            ScoreType.RECENT,
                            Integer.parseInt(n),
                            true
                    );
                    final UserExtended user = NetworkHelper.getUser(id, tokenManager.getTokenData());

                    return renderer.render(user, scores, ScoreType.RECENT);
                }, delayMillis).thenAccept(image -> ctx.status(200).contentType("img/png").result(image)),
                "Recent scores request failed! Param:{n=" + n + ", id:" + id + "}"
        );
    }

    private void getMultiplayerRooms(@NotNull Context context) {
        LOG.info("{} - mp", context.ip());

        contextFutureWithError(context,
                executor.runAsync(() -> {
                    final List<MultiplayerRoom> rooms = NetworkHelper.getRooms(tokenManager.getTokenData());
                    rooms.sort(Comparator.comparingInt(MultiplayerRoom::getParticipantCount));

                    List<MultiplayerRoom> topRooms = rooms.size() > 20 ? rooms.reversed().subList(0, 20) : rooms;
                    JsonArray arr = new JsonArray();
                    for (MultiplayerRoom room : topRooms) {
                        arr.add(room.getName() + " << " + room.getParticipantCount());
                    }
                    return arr;
                }, delayMillis).thenAccept(arr -> context.status(200).json(new Response(true, "Success", arr).toString())),
                "Multiplayer rooms request failed!"
        );
    }

    private void getDaily(@NotNull Context context) {
        LOG.info("{} - daily", context.ip());

        contextFutureWithError(context,
                executor.runAsync(() -> NetworkHelper.getRooms(tokenManager.getTokenData()).stream()
                        .filter(room -> Objects.equals(room.getCategory(), "daily_challenge"))
                        .findFirst(), delayMillis).thenAccept(roomResult -> {
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
                executor.runAsync(() -> {
                    final List<Score> scores = NetworkHelper.getUserScores(
                            tokenManager.getTokenData(),
                            id,
                            ScoreType.BEST,
                            Integer.parseInt(n),
                            false
                    );
                    final UserExtended user = NetworkHelper.getUser(id, tokenManager.getTokenData());

                    return renderer.render(user, scores, ScoreType.BEST);
                }, delayMillis).thenAccept(image -> ctx.status(200).contentType("img/png").result(image)),
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
