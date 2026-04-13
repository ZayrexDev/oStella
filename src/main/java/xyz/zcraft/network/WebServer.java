package xyz.zcraft.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.nanamochi.rosu_pp_jar.Performance;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.model.Mod;
import xyz.zcraft.model.MultiplayerRoom;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.score.Placement;
import xyz.zcraft.model.score.Score;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.model.user.UserExtended;
import xyz.zcraft.service.AsyncService;
import xyz.zcraft.service.BeatmapCacheService;
import xyz.zcraft.service.ScoreRenderService;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.TokenManager;

import java.io.IOException;
import java.util.*;

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
                    .get("pk", this::getPK)
                    .get("status", this::getServerStatus)
                    .before(ctx -> LOG.info("{} - {} {}", ctx.ip(), ctx.method(), ctx.path()))
                    .exception(Exception.class, (e, ctx) -> LOG.error("An error occurred while processing request: {}", ctx.queryString(), e));

            if (conf.debug()) {
                LOG.warn("/bypass endpoint is enabled in debug mode! To prevent security risks, please disable debug mode in production environment.");
                cfg.routes.get("bypass", this::bypassRequest);
            }
        });
    }

    private void getPK(@NotNull Context context) throws Exception {
        final String bm = context.queryParam("bm");
        final String us = context.queryParam("u");

        if (bm == null || us == null || !isInteger(bm)) {
            context.status(400).result("Invalid query parameter!");
            return;
        }

        final String[] u = us.split(",");

        final LinkedList<Placement> placements = new LinkedList<>();
        for (String s : u) {
            final Score userScore = executor.runDelayAsync(() -> OsuAPI.getUserScore(tokenManager.getTokenData(), s, bm)).get();
            if (userScore == null) continue;

            final UserExtended user = executor.runDelayAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), s)).get();
            if (user == null) continue;

            if(userScore.getPp() == null) continue;

            final Placement placement = new Placement();
            placement.setScore(userScore);
            placement.setUser(user);

            placements.add(placement);
        }

        placements.sort((a, b) -> (int) (b.score.pp - a.score.pp));

        final BeatmapExtended beatmap = executor.runDelayAsync(() -> OsuAPI.getBeatmap(tokenManager.getTokenData(), bm)).get();

        final io.github.nanamochi.rosu_pp_jar.Beatmap rosuBeatmap = executor.runDelayAsync(() -> cacheService.getRosuBeatmap(bm, false)).get();

        final Performance perfSS = Performance.create(rosuBeatmap);
        perfSS.setAccuracy(100.0);
        perfSS.setMisses(0);
        perfSS.setCombo(beatmap.getMaxCombo());

        final byte[] imgByte = renderer.renderPK(beatmap, placements, perfSS.calculate().pp());

        context.status(200).result(imgByte);
    }

    private void getBeatmap(@NotNull Context context) throws Exception {
        final String bm = context.queryParam("bm");

        if (bm == null || !isInteger(bm)) {
            context.status(400).result("Invalid query parameter!");
            return;
        }

        final BeatmapExtended beatmap = executor.runDelayAsync(() -> OsuAPI.getBeatmap(tokenManager.getTokenData(), bm)).get();
        final io.github.nanamochi.rosu_pp_jar.Beatmap rosuBeatmap = executor.runDelayAsync(() -> cacheService.getRosuBeatmap(bm, false)).get();

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

        final byte[] bytes = renderer.renderBeatmap(
                beatmap,
                perfSS.calculate().pp().intValue(),
                perfFC.calculate().pp().intValue(),
                perf95.calculate().pp().intValue()
        );

        context.status(200).result(bytes);
    }

    private void bypassRequest(@NotNull Context context) throws Exception {
        final JsonObject jsonObject = executor.runDelayAsync(() -> OsuAPI.byPassRequest(tokenManager.getTokenData(), context.queryString())).get();
        context.status(200).json(new Response(true, "Success", jsonObject).toString());
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
        context.status(200).result(new Response(true, "Server is running!", null).toString());
    }

    private void getRecentScores(@NotNull Context ctx) throws Exception {
        final String u = ctx.queryParam("u");
        final String n = ctx.queryParam("n");

        if (u == null || n == null || !isInteger(n)) {
            ctx.status(400).result("Invalid query parameter!");
            return;
        }

        final List<Score> scores = executor.runAsync(() -> OsuAPI.getUserScores(
                tokenManager.getTokenData(),
                u,
                ScoreType.RECENT,
                Integer.parseInt(n),
                false
        )).get();

        final UserExtended user = executor.runDelayAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), u)).get();

        final byte[] bytes = renderer.renderScores(user, scores, ScoreType.RECENT);

        ctx.status(200).result(bytes);
    }

    private void getMultiplayerRooms(@NotNull Context context) throws Exception {
        final List<MultiplayerRoom> rooms = executor.runDelayAsync(() -> OsuAPI.getRooms(tokenManager.getTokenData())).get();
        rooms.sort(Comparator.comparingInt(MultiplayerRoom::getParticipantCount));

        List<MultiplayerRoom> topRooms = rooms.size() > 20 ? rooms.reversed().subList(0, 20) : rooms;
        JsonArray arr = new JsonArray();
        for (MultiplayerRoom room : topRooms) {
            arr.add(room.getName() + " << " + room.getParticipantCount());
        }

        context.status(200).json(new Response(true, "Success", arr).toString());
    }

    private void getDaily(@NotNull Context context) throws Exception {
        final Optional<MultiplayerRoom> roomResult = executor.runDelayAsync(() -> OsuAPI.getRooms(tokenManager.getTokenData())).get().stream()
                .filter(room -> Objects.equals(room.getCategory(), "daily_challenge"))
                .findFirst();
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
    }

    private void getBestOfN(@NotNull Context ctx) throws Exception {
        final String u = ctx.queryParam("u");
        final String n = ctx.queryParam("n");

        if (u == null || n == null || !isInteger(n)) {
            ctx.status(400)
                    .result("Invalid query parameter!");
            return;
        }

        final List<Score> scores = executor.runDelayAsync(() -> OsuAPI.getUserScores(
                tokenManager.getTokenData(),
                u,
                ScoreType.BEST,
                Integer.parseInt(n),
                false
        )).get();
        final UserExtended user = executor.runDelayAsync(() -> OsuAPI.getUser(tokenManager.getTokenData(), u)).get();

        final byte[] bytes = renderer.renderScores(user, scores, ScoreType.BEST);
        ctx.status(200).result(bytes);
    }
}
