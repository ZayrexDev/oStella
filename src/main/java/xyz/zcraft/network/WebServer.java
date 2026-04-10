package xyz.zcraft.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.data.*;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.ScoreRenderer;
import xyz.zcraft.util.TokenManager;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static xyz.zcraft.util.FormatUtil.isInteger;

public class WebServer {
    private static final Logger LOG = LogManager.getLogger(WebServer.class);

    private final ScoreRenderer renderer;
    private final AsyncHelper asyncHelper;
    private final Config conf;
    private final TokenManager tokenManager;
    private final Javalin app;

    public WebServer(Config conf, TokenManager tokenManager) {
        this.conf = conf;
        this.tokenManager = tokenManager;
        renderer = new ScoreRenderer();
        asyncHelper = new AsyncHelper(Integer.parseInt(conf.maxThreads()));
        app = Javalin.create(cfg -> cfg.routes
                .get("bo", this::getBestOfN)
                .get("daily", this::getDaily)
                .get("mp", this::getMultiplayerRooms)
                .get("rs", this::getRecentScores)
                .get("status", this::getServerStatus));
    }

    public void start() {
        app.start(Integer.parseInt(conf.port()));
    }

    public void close() {
        app.stop();
        asyncHelper.close();
    }

    private void getServerStatus(@NotNull Context context) {
        context.json(new Response(true, "Server is running!", null));
    }

    private void getRecentScores(@NotNull Context ctx) throws ExecutionException, InterruptedException {
        final String id = ctx.queryParam("id");
        final String n = ctx.queryParam("n");

        LOG.info("Incoming request from {} - recent - {} - {}", ctx.ip(), id, n);

        if (id == null || n == null || !isInteger(n)) {
            ctx.status(400).result("Invalid query parameter!");
            return;
        }

        ctx.contentType("img/png");
        ctx.result(asyncHelper.runAsync(() -> {
            try {
                Thread.sleep(Integer.parseInt(conf.delay()));
            } catch (InterruptedException e) {
                LOG.error("Interrupted while waiting for request!", e);
            }
            final List<Score> scores = NetworkHelper.getUserScores(
                    tokenManager.getTokenData(),
                    id,
                    ScoreType.RECENT,
                    Integer.parseInt(Objects.requireNonNull(n)),
                    true
            );
            final UserExtended user = NetworkHelper.getUser(id, tokenManager.getTokenData());

            return renderer.render(user, scores, ScoreType.RECENT);
        }).get());
    }

    private void getMultiplayerRooms(@NotNull Context context) throws ExecutionException, InterruptedException {
        context.result(asyncHelper.runAsync(() -> {
            final List<MultiplayerRoom> rooms = NetworkHelper.getRooms(tokenManager.getTokenData());
            rooms.sort(Comparator.comparingInt(MultiplayerRoom::getParticipantCount));

            List<MultiplayerRoom> topRooms = rooms.size() > 20 ? rooms.reversed().subList(0, 20) : rooms;
            JsonArray arr = new JsonArray();
            for (MultiplayerRoom room : topRooms) {
                arr.add(room.getName() + " << " + room.getParticipantCount());
            }
            return new Response(true, "Success", arr).toString();
        }).exceptionally(throwable -> new Response(false, throwable.getMessage(), null).toString()).get());
    }

    private void getDaily(@NotNull Context context) {
        asyncHelper.runAsync(() -> {
            NetworkHelper.getRooms(tokenManager.getTokenData()).stream()
                    .filter(room -> Objects.equals(room.getCategory(), "daily_challenge"))
                    .findFirst()
                    .ifPresentOrElse(room -> {
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
                    }, () -> context.status(404).result(new Response(false, "Daily challenge room not found!", null).toString()));
            return null;
        });
    }

    private void getBestOfN(@NotNull Context ctx) throws InterruptedException, ExecutionException {
        final String id = ctx.queryParam("id");
        final String n = ctx.queryParam("n");

        LOG.info("Incoming request from {} - boN - {} - {}", ctx.ip(), id, n);

        if (id == null || n == null || !isInteger(n)) {
            ctx.status(400).result("Invalid query parameter!");
            return;
        }

        ctx.contentType("img/png");
        ctx.result(asyncHelper.runAsync(() -> {
            try {
                Thread.sleep(Integer.parseInt(conf.delay()));
            } catch (InterruptedException e) {
                LOG.error("Interrupted while waiting for request!", e);
            }
            final List<Score> scores = NetworkHelper.getUserScores(
                    tokenManager.getTokenData(),
                    id,
                    ScoreType.BEST,
                    Integer.parseInt(Objects.requireNonNull(n)),
                    false
            );
            final UserExtended user = NetworkHelper.getUser(id, tokenManager.getTokenData());

            return renderer.render(user, scores, ScoreType.BEST);
        }).get());
    }
}
