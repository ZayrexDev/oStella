package xyz.zcraft.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import desu.life.RosuFFI;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.model.Mod;
import xyz.zcraft.model.MultiplayerRoom;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.DiffSpec;
import xyz.zcraft.model.score.Placement;
import xyz.zcraft.model.score.Score;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.model.user.Statistics;
import xyz.zcraft.model.user.StatisticsRuleset;
import xyz.zcraft.model.user.User;
import xyz.zcraft.service.AsyncService;
import xyz.zcraft.service.BeatmapCacheService;
import xyz.zcraft.service.ScoreRenderService;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.TokenManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

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
                    .get("m", this::getBeatmap)
                    .get("pk", this::getPK)
                    .get("lb", this::getLeaderBoard)
                    .get("status", this::getServerStatus)
                    .before(ctx -> LOG.info("{} - {} {}", ctx.ip(), ctx.method(), ctx.path()))
                    .exception(Exception.class, (e, ctx) -> LOG.error("An error occurred while processing request: {}", ctx.queryString(), e));

            if (conf.debug()) {
                LOG.warn("/bypass endpoint is enabled in debug mode! To prevent security risks, please disable debug mode in production environment.");
                cfg.routes.get("bypass", this::bypassRequest);
            }
        });
    }

    private void getLeaderBoard(@NotNull Context context) {
        final String us = context.queryParam("u");

        if (us == null) {
            context.status(400).result(new Response(false, "Invalid query parameter!", null).toString());
            return;
        }

        final String[] u = us.split(",");
        final List<String> ids = Arrays.stream(u).distinct().toList();
        final LinkedList<User> users = new LinkedList<>();

        for (int i = 0; i < ids.size(); i += 50) {
            final List<String> subList = ids.subList(i, Math.min(i + 50, ids.size()));
            executor.enqueue(() -> OsuAPI.getUsers(tokenManager.getTokenData(), subList)).ifPresent(users::addAll);
        }

        users.sort(Comparator.comparingDouble((User user) ->
                Optional.ofNullable(user)
                        .map(User::getStatisticsRulesets)
                        .map(StatisticsRuleset::getOsu)
                        .map(Statistics::getPp)
                        .orElse(0.0)
        ).reversed());

        final byte[] imgByte = renderer.renderLeaderboard(users);

        context.status(200).result(imgByte);
    }

    private void getPK(@NotNull Context context) throws RosuFFI.FFIException {
        final String m = context.queryParam("m");
        final String us = context.queryParam("u");

        if (m == null || us == null || !isInteger(m)) {
            context.status(400).result(new Response(false, "Invalid query parameter!", null).toString());
            return;
        }

        final String[] u = Arrays.stream(us.split(",")).distinct().toArray(String[]::new);

        final LinkedList<Placement> placements = new LinkedList<>();
        for (String s : u) {
            final var userScore = executor.enqueue(() -> OsuAPI.getUserScore(tokenManager.getTokenData(), s, m));
            if (userScore.isEmpty() || userScore.get().getPp() == null) continue;

            final var user = executor.enqueue(() -> OsuAPI.getUser(tokenManager.getTokenData(), s));
            if (user.isEmpty()) continue;


            final Placement placement = new Placement();
            placement.setScore(userScore.get());
            placement.setUser(user.get());

            placements.add(placement);
        }

        placements.sort((a, b) -> (int) (b.score.pp - a.score.pp));

        final var beatmap = executor.enqueue(() -> OsuAPI.getBeatmap(tokenManager.getTokenData(), m));

        if (beatmap.isEmpty()) {
            context.status(400).result(new Response(false, "No beatmap found", null).toString());
            return;
        }

        final Optional<byte[]> enqueue = executor.enqueue(() -> cacheService.getRosuBeatmapBytes(m, false));

        if (enqueue.isEmpty()) {
            context.status(400).result(new Response(false, "Beatmap PP calculation failed", null).toString());
            return;
        }

        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(enqueue.get());
             final RosuFFI.Performance perfSS = new RosuFFI.Performance()
        ) {


            perfSS.setAccuracy(100.0);
            perfSS.setMisses(0);
            perfSS.setCombo(beatmap.get().getMaxCombo());

            final byte[] imgByte = renderer.renderPK(beatmap.get(), placements, perfSS.calculate(rosuBeatmap).osu.t.pp);

            context.status(200).result(imgByte);
        }
    }

    private void getBeatmap(@NotNull Context context) throws RosuFFI.FFIException {
        final String m = context.queryParam("m");

        if (m == null || !isInteger(m)) {
            context.status(400).result(new Response(false, "Invalid query parameter!", null).toString());
            return;
        }

        final var beatmap = executor.enqueue(() -> OsuAPI.getBeatmap(tokenManager.getTokenData(), m));

        if (beatmap.isEmpty()) {
            context.status(400).result(new Response(false, "No beatmap found", null).toString());
            return;
        }

        final String rosuBeatmapPath = cacheService.getRosuBeatmapPath(m, false);

//        if (enqueue.isEmpty()) {
//            context.status(400).result(new Response(false, "No beatmap found", null).toString());
//            return;
//        }

        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(rosuBeatmapPath);
             final RosuFFI.Performance perfSS = new RosuFFI.Performance();
             final RosuFFI.Performance perfFC = new RosuFFI.Performance();
             final RosuFFI.Performance perf95 = new RosuFFI.Performance()
        ) {

            System.out.println("rosuBeatmap.mode() = " + rosuBeatmap.mode());
            System.out.println("rosuBeatmap.bpm() = " + rosuBeatmap.bpm());

            perfSS.setAccuracy(100.0);
            perfSS.setMisses(0);
            perfSS.setCombo(beatmap.get().getMaxCombo());

            perfFC.setAccuracy(98.0);
            perfFC.setMisses(0);
            perfFC.setCombo(beatmap.get().getMaxCombo());

            perf95.setAccuracy(95.0);

            final DiffSpec diffSpec = new DiffSpec();

            final RosuFFI.RosuPPLib.PerformanceAttributes cal = perfSS.calculate(rosuBeatmap);
            diffSpec.setPpSS(cal.osu.t.pp);
            diffSpec.setPpFC(perfFC.calculate(rosuBeatmap).osu.t.pp);
            diffSpec.setPp95(perf95.calculate(rosuBeatmap).osu.t.pp);

            final var attr = cal.osu.t.difficulty;

            System.out.println("mode=" + cal.mode);
            System.out.println("osu.is_some=" + cal.osu.is_some);
            System.out.println("taiko.is_some=" + cal.taiko.is_some);
            System.out.println("fruit.is_some=" + cal.fruit.is_some);
            System.out.println("mania.is_some=" + cal.mania.is_some);

            diffSpec.setAim(attr.aim);
            diffSpec.setSpeed(attr.speed);

            final byte[] bytes = renderer.renderBeatmap(
                    beatmap.get(),
                    diffSpec
            );

            context.status(200).result(bytes);
        }
    }

    private void bypassRequest(@NotNull Context context) {
        executor.enqueue(() -> OsuAPI.byPassRequest(tokenManager.getTokenData(), context.queryString()))
                .ifPresent(r -> context.status(200).result(new Response(true, "Success", r).toString()));
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
            ctx.status(400).result(new Response(false, "Invalid query parameter!", null).toString());
            return;
        }

        final List<Score> scores = executor.runAsync(() -> OsuAPI.getUserScores(
                tokenManager.getTokenData(),
                u,
                ScoreType.RECENT,
                Integer.parseInt(n),
                false
        )).get();

        final var user = executor.enqueue(() -> OsuAPI.getUser(tokenManager.getTokenData(), u));

        if (user.isEmpty()) {
            ctx.status(400).result(new Response(false, "No user found", null).toString());
            return;
        }

        final byte[] bytes = renderer.renderScores(user.get(), scores, ScoreType.RECENT);

        ctx.status(200).result(bytes);
    }

    private void getMultiplayerRooms(@NotNull Context context) {
        final var roomsOptional = executor.enqueue(() -> OsuAPI.getRooms(tokenManager.getTokenData()));
        if (roomsOptional.isEmpty()) {
            context.status(400).result(new Response(false, "No rooms found", null).toString());
            return;
        }
        final var rooms = roomsOptional.get();

        rooms.sort(Comparator.comparingInt(MultiplayerRoom::getParticipantCount));

        List<MultiplayerRoom> topRooms = rooms.size() > 20 ? rooms.reversed().subList(0, 20) : rooms;
        JsonArray arr = new JsonArray();
        for (MultiplayerRoom room : topRooms) {
            arr.add(room.getName() + " << " + room.getParticipantCount());
        }

        context.status(200).result(new Response(true, "Success", arr).toString());
    }

    private void getDaily(@NotNull Context context) {
        final var roomResult = executor.enqueue(() -> OsuAPI.getRooms(tokenManager.getTokenData()))
                .map(List::stream)
                .map(s -> s.filter(room -> Objects.equals(room.getCategory(), "daily_challenge")))
                .flatMap(Stream::findFirst);
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

        context.status(200).result(new Response(true, "Success", data).toString());
    }

    private void getBestOfN(@NotNull Context ctx) {
        final String u = ctx.queryParam("u");
        final String n = ctx.queryParam("n");

        if (u == null || n == null || !isInteger(n)) {
            ctx.status(400)
                    .result(new Response(false, "Invalid query parameter!", null).toString());
            return;
        }

        final var scores = executor.enqueue(() -> OsuAPI.getUserScores(
                tokenManager.getTokenData(),
                u,
                ScoreType.BEST,
                Integer.parseInt(n),
                false
        ));

        if (scores.isEmpty()) {
            ctx.status(400).result(new Response(false, "No user found", null).toString());
            return;
        }

        final var user = executor.enqueue(() -> OsuAPI.getUser(tokenManager.getTokenData(), u));

        if (user.isEmpty()) {
            ctx.status(400).result(new Response(false, "No user found", null).toString());
            return;
        }

        final byte[] bytes = renderer.renderScores(user.get(), scores.get(), ScoreType.BEST);
        ctx.status(200).result(bytes);
    }
}
