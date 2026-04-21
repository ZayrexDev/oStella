package xyz.zcraft.network;

import com.google.gson.*;
import desu.life.RosuFFI;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.model.Mod;
import xyz.zcraft.model.MultiplayerRoom;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.Beatmapset;
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
import xyz.zcraft.service.ReplayRenderService;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.TokenManager;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static xyz.zcraft.util.MiscUtil.isInteger;
import static xyz.zcraft.util.MiscUtil.isLong;

public class Router implements Closeable {
    private static final Logger LOG = LogManager.getLogger(Router.class);
    private final Gson GSON = new Gson();
    private final RenderService renderer;
    private final CacheService cacheService;
    private final AsyncService executor;
    private final TokenManager tokenManager;
    private final ReplayRenderService replayRenderService;

    public Router(Config conf, TokenManager tokenManager) throws IOException {
        this.tokenManager = tokenManager;
        this.executor = new AsyncService(conf.maxThreads(), conf.delay());
        this.cacheService = new CacheService();
        this.renderer = new RenderService(cacheService);
        if(conf.danserPath() != null) {
            this.replayRenderService = new ReplayRenderService(Path.of(conf.danserPath()), cacheService.getDanserCache());
        } else {
            this.replayRenderService = null;
        }
    }

    protected void getScoreRef(@NotNull Context context) {
        final String of = context.queryParam("of");
        final String iStr = context.queryParam("i");
        final String u = context.queryParam("u");

        if (u == null || iStr == null || of == null) {
            context.status(400).result(Response.error("Invalid query parameter! Missing u", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        if (!((of.startsWith("rs") || of.startsWith("bo")) && isInteger(iStr))) {
            context.status(400).result(
                    Response.error("Invalid query parameter! 'of' should starts with 'rs' or 'bo' and ends with a number!", ErrorCode.ILLEGAL_ARGUMENT).toString()
            );
            return;
        }

        final int num = Integer.parseInt(iStr);

        final Optional<List<Score>> rs = executor.enqueue(() -> OsuAPI.getUserScores(tokenManager.getTokenData(), u, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, num, false));

        if (rs.isEmpty() || rs.get().size() < num) {
            context.status(400).result(Response.error("No scores found!", ErrorCode.NO_SCORE_FOUND).toString());
            return;
        }

        final var scoreId = rs.get().getLast().getId();
        executor.enqueue(() -> OsuAPI.getScore(tokenManager.getTokenData(), String.valueOf(scoreId)))
                .ifPresentOrElse(score -> {
                    try {
                        final DiffSpec diffSpec = getDiffSpecForMap(score.getBeatmap(), score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));
                        final byte[] bytes = renderer.renderScore(score, diffSpec);
                        context.status(200).result(bytes);
                    } catch (Exception e) {
                        context.status(500).result(Response.error("An error occurred while processing the request!", ErrorCode.SCORE_FETCH_FAILED).toString());
                        LOG.error("An error occurred while processing request: {}", context.queryString(), e);
                    }
                }, () -> context.status(400).result(Response.error("No scores found!", ErrorCode.NO_SCORE_FOUND).toString()));
    }

    protected void getScore(@NotNull Context context) throws Exception {
        if (context.queryParam("of") != null) {
            getScoreRef(context);
        } else {
            getScoreOfId(context);
        }
    }

    private void getScoreOfId(@NotNull Context context) throws RosuFFI.FFIException {
        final String s = context.queryParam("s");

        if (s == null || !isLong(s)) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        final var scoreOptional = executor.enqueue(() -> OsuAPI.getScore(tokenManager.getTokenData(), s));

        if (scoreOptional.isEmpty()) {
            context.status(400).result(Response.error("No score found.", ErrorCode.NO_SCORE_FOUND).toString());

            return;
        }

        final Score score = scoreOptional.get();

        final BeatmapExtended beatmap = score.getBeatmap();

        final DiffSpec diffSpec = getDiffSpecForMap(beatmap, score.getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));

        final byte[] bytes = renderer.renderScore(score, diffSpec);

        context.status(200).result(bytes);
    }

    protected void searchBeatmapSet(@NotNull Context context) {
        final String query = context.queryParam("q");

        if (query == null) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        executor.enqueue(() -> OsuAPI.searchBeatmapset(tokenManager.getTokenData(), query)).ifPresentOrElse(
                result -> {
                    final List<SearchResultItem> list = result.stream().map(SearchResultItem::fromBeatmapset).toList();
                    context.status(200).result(new Response(true, "Query successful", GSON.toJsonTree(list)).toString());
                },
                () -> context.status(400).result(Response.error("No beatmapset found", ErrorCode.NO_BEATMAP_FOUND).toString())
        );
    }

    protected void getLeaderBoard(@NotNull Context context) {
        final String us = context.queryParam("u");

        if (us == null) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
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

    private void getPKRef(@NotNull Context context) throws Exception {
        final String of = context.queryParam("of");
        final String iStr = context.queryParam("i");
        final String uSource = context.queryParam("us");
        final String us = context.queryParam("u");

        if (uSource == null || us == null || of == null || iStr == null) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        if (!((of.startsWith("rs") || of.startsWith("bo")) && isInteger(iStr))) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        final int num = Integer.parseInt(iStr);

        final Optional<List<Score>> scoresOptional = executor.enqueue(() -> OsuAPI.getUserScores(tokenManager.getTokenData(), uSource, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, num, false));

        if (scoresOptional.isEmpty() || scoresOptional.get().size() < num) {
            context.status(400).result(Response.error("No scores found for user!", ErrorCode.NO_SCORE_FOUND).toString());
            return;
        }

        final Long id = scoresOptional.get().getLast().getBeatmap().getId();

        final String[] u = Arrays.stream(us.split(",")).distinct().toArray(String[]::new);

        final LinkedList<Placement> placements = new LinkedList<>();
        for (String s : u) {
            final var userScore = executor.enqueue(() -> OsuAPI.getUserScore(tokenManager.getTokenData(), s, String.valueOf(id)));
            if (userScore.isEmpty() || userScore.get().getPp() == null) continue;

            final var user = executor.enqueue(() -> OsuAPI.getUser(tokenManager.getTokenData(), s));
            if (user.isEmpty()) continue;


            final Placement placement = new Placement();
            placement.setScore(userScore.get());
            placement.setUser(user.get());

            placements.add(placement);
        }

        placements.sort((a, b) -> (int) (b.score.pp - a.score.pp));

        final var beatmap = executor.enqueue(() -> OsuAPI.getBeatmap(tokenManager.getTokenData(), String.valueOf(id)));

        if (beatmap.isEmpty()) {
            context.status(400).result(Response.error("No beatmap found", ErrorCode.NO_BEATMAP_FOUND).toString());
            return;
        }

        final String rosuBeatmapPath = cacheService.getRosuBeatmapPath(String.valueOf(id), false);

        renderPKFinal(context, placements, beatmap.get(), rosuBeatmapPath);
    }

    protected void renderPKFinal(@NotNull Context context, LinkedList<Placement> placements, BeatmapExtended beatmap, String rosuBeatmapPath) throws RosuFFI.FFIException {
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(rosuBeatmapPath);
             final RosuFFI.Performance perfSS = new RosuFFI.Performance()
        ) {
            perfSS.setAccuracy(100.0);
            perfSS.setMisses(0);
            perfSS.setCombo(beatmap.getMaxCombo());

            final byte[] imgByte = renderer.renderPK(beatmap, placements, perfSS.calculate(rosuBeatmap).osu.t.pp);

            context.status(200).result(imgByte);
        }
    }

    protected void getPK(@NotNull Context context) throws Exception {
        if (context.queryParam("of") != null) {
            getPKRef(context);
        } else {
            getPKOfIds(context);
        }
    }

    private void getPKOfIds(@NotNull Context context) throws RosuFFI.FFIException {
        final String m = context.queryParam("m");
        final String us = context.queryParam("u");

        if (m == null || us == null || !isInteger(m)) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
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
            context.status(400).result(Response.error("No beatmap found", ErrorCode.NO_BEATMAP_FOUND).toString());
            return;
        }

        final String rosuBeatmapPath = cacheService.getRosuBeatmapPath(m, false);

        renderPKFinal(context, placements, beatmap.get(), rosuBeatmapPath);
    }

    private void getBeatmapRef(@NotNull Context context) {
        final String of = Objects.requireNonNull(context.queryParam("of"));
        final String iStr = Objects.requireNonNull(context.queryParam("i"));
        final String u = context.queryParam("u");

        if (u == null) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        if (!((of.startsWith("rs") || of.startsWith("bo")) && isInteger(iStr))) {
            context.status(400).result(Response.error(
                    "Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString()
            );
            return;
        }

        final int num = Integer.parseInt(iStr);

        final Optional<List<Score>> scoresOptional = executor.enqueue(() -> OsuAPI.getUserScores(tokenManager.getTokenData(), u, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, num, false));

        if (scoresOptional.isEmpty() || scoresOptional.get().size() < num) {
            context.status(400).result(Response.error("No scores found for user!", ErrorCode.NO_SCORE_FOUND).toString());
            return;
        }

        final List<Score> scores = scoresOptional.get();
        final var beatmapId = scores.getLast().getBeatmap().getId();
        final var beatmapsetId = scores.getLast().getBeatmapset().getId();
        final Optional<Beatmapset> enqueue = executor.enqueue(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), String.valueOf(beatmapsetId)));

        if (enqueue.isEmpty()) {
            context.status(400).result(Response.error("No beatmapset found!", ErrorCode.NO_BEATMAPSET_FOUND).toString());
            return;
        }

        final Beatmapset beatmapset = enqueue.get();

        final Optional<BeatmapExtended> beatmapOptional = beatmapset.getBeatmaps().stream()
                .filter(b -> Objects.equals(b.getId(), beatmapId))
                .findFirst();

        if (beatmapOptional.isEmpty()) {
            context.status(400).result(Response.error("No beatmap found!", ErrorCode.NO_BEATMAP_FOUND).toString());
            return;
        }

        final BeatmapExtended beatmapExtended = beatmapOptional.get();

        beatmapExtended.setBeatmapset(beatmapset);

        try {
            final DiffSpec diffSpec = getDiffSpecForMap(beatmapExtended, scores.getLast().getModsList().stream().map(Mod::getAcronym).reduce("", String::concat));
            final byte[] bytes = renderer.renderBeatmap(beatmapExtended, diffSpec);
            context.status(200).result(bytes);
        } catch (Exception e) {
            context.status(500).result(Response.error("An error occurred while processing the request!", ErrorCode.BEATMAP_FETCH_FAILED).toString());
            LOG.error("An error occurred while processing request: {}", context.queryString(), e);
        }
    }

    protected void getBeatmapOfSet(@NotNull Context context) throws Exception {
        final String ms = context.queryParam("ms");
        final String iStr = context.queryParam("i");
        final String mod = context.queryParam("mod");

        if (iStr == null) {
            context.status(400).result(Response.error("Invalid query parameter! Missing i", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        if (!isInteger(ms, iStr)) {
            context.status(400).result(Response.error(
                    "Invalid query parameter! 'ms' and 'i' should be a number!", ErrorCode.ILLEGAL_ARGUMENT).toString()
            );
            return;
        }

        final int i = Integer.parseInt(iStr);

        final Optional<Beatmapset> enqueue = executor.enqueue(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), ms));

        if (enqueue.isEmpty() || enqueue.get().getBeatmaps().size() < i) {
            context.status(400).result(Response.error("No beatmap found", ErrorCode.NO_BEATMAP_FOUND).toString());
            return;
        }

        final List<BeatmapExtended> beatmaps = enqueue.get().getBeatmaps();

        beatmaps.sort(Comparator.comparingDouble(BeatmapExtended::getDifficultyRating));

        final BeatmapExtended beatmapExtended = beatmaps.get(i - 1);
        beatmapExtended.setBeatmapset(enqueue.get());

        DiffSpec spec = getDiffSpecForMap(beatmapExtended, mod);
        final byte[] bytes = renderer.renderBeatmap(beatmapExtended, spec);
        context.status(200).result(bytes);
    }

    protected void getBeatmap(@NotNull Context context) throws Exception {
        if (context.queryParam("of") != null) {
            getBeatmapRef(context);
        } else if (context.queryParam("ms") != null) {
            getBeatmapOfSet(context);
        } else {
            getBeatmapOfId(context);
        }
    }

    private void getBeatmapOfId(@NotNull Context context) throws RosuFFI.FFIException {
        final String m = context.queryParam("m");
        final String mod = context.queryParam("mod");

        if (m == null || !isInteger(m)) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        final var beatmapsetOptional = executor.enqueue(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m));

        if (beatmapsetOptional.isEmpty()) {
            context.status(400).result(Response.error("No beatmapset found", ErrorCode.NO_BEATMAPSET_FOUND).toString());
            return;
        }

        final Beatmapset beatmapset = beatmapsetOptional.get();

        final Optional<BeatmapExtended> optional = beatmapset.getBeatmaps().stream()
                .filter(b -> Objects.equals(String.valueOf(b.getId()), m))
                .findFirst();

        if (optional.isEmpty()) {
            context.status(400).result(Response.error("No beatmap found", ErrorCode.NO_BEATMAP_FOUND).toString());
            return;
        }

        final BeatmapExtended beatmap = optional.get();

        beatmap.setBeatmapset(beatmapset);

        final DiffSpec diffSpec = getDiffSpecForMap(beatmap, mod);

        final byte[] bytes = renderer.renderBeatmap(
                beatmap,
                diffSpec
        );

        context.status(200).result(bytes);
    }

    private DiffSpec getDiffSpecForMap(BeatmapExtended beatmap, String mod) throws RosuFFI.FFIException {
        final String rosuBeatmapPath = cacheService.getRosuBeatmapPath(String.valueOf(beatmap.getId()), false);

        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(rosuBeatmapPath);
             final RosuFFI.Performance perf = new RosuFFI.Performance()
        ) {
            final DiffSpec diffSpec = new DiffSpec();

            final RosuFFI.Mods mods = RosuFFI.Mods.fromAcronyms(mod == null ? "" : mod, RosuFFI.Mode.Osu);

            mods.removeUnknownMods();
            mods.sanitize();

            perf.setMods(mods);

            perf.setAccuracy(98.0);
            perf.setMisses(0);

            var calc = perf.calculate(rosuBeatmap);
            diffSpec.setPpFC(calc.osu.t.pp);

            perf.setAccuracy(95.0);

            calc = perf.calculate(rosuBeatmap);
            diffSpec.setPp95(calc.osu.t.pp);


            perf.setAccuracy(100.0);
            perf.setMisses(0);

            calc = perf.calculate(rosuBeatmap);
            diffSpec.setPpSS(calc.osu.t.pp);

            final RosuFFI.RosuPPLib.ScoreState scoreState = perf.generateState(rosuBeatmap);

            final var attr = calc.osu.t.difficulty;
            diffSpec.setAim(attr.aim);
            diffSpec.setSpeed(attr.speed);

            diffSpec.setBpm(beatmap.getBpm());
            diffSpec.setLength(beatmap.getHitLength());
            diffSpec.setTotalLength(beatmap.getTotalLength());
            diffSpec.setStar(calc.osu.t.difficulty.stars);

            if (mod != null && !mod.isEmpty()) {
                diffSpec.setModStr(mod);
                diffSpec.setModded(true);
            }

            diffSpec.setAr(calc.osu.t.difficulty.ar);
            diffSpec.setHp(calc.osu.t.difficulty.hp);
            diffSpec.setCs(beatmap.cs);
            diffSpec.setOd(beatmap.accuracy);

            // Calculate BPM CS Length

            if (mods.contains("HR")) {
                diffSpec.setCs(Math.min(diffSpec.getCs() * 1.3, 10));
            } else if (mods.contains("EZ")) {
                diffSpec.setCs(Math.min(diffSpec.getCs() * 0.5, 10));
            }

            if (mods.contains("DT") || mods.contains("NC")) {
                diffSpec.setBpm(diffSpec.getBpm() * 1.5);
                diffSpec.setLength(diffSpec.getLength() / 1.5);
                diffSpec.setTotalLength(diffSpec.getTotalLength() / 1.5);
            } else if (mods.contains("HT") || mods.contains("DC")) {
                diffSpec.setBpm(diffSpec.getBpm() * 0.75);
                diffSpec.setLength(diffSpec.getLength() / 0.75);
                diffSpec.setTotalLength(diffSpec.getTotalLength() / 0.75);
            }

            // Calculate OD

            boolean changingOd = false;
            double od = beatmap.getAccuracy();
            if (mods.contains("HR")) {
                changingOd = true;
                od = Math.min(od * 1.4, 10);
            } else if (mods.contains("EZ")) {
                changingOd = true;
                od = od * 0.5;
            }

            double window = 80.0 - (6.0 * od);

            if (mods.contains("DT") || mods.contains("NC")) {
                changingOd = true;
                window = window / 1.5;
            } else if (mods.contains("HT") || mods.contains("DC")) {
                changingOd = true;
                window = window / 0.75;
            }

            od = (80.0 - window) / 6;

            if (changingOd) {
                diffSpec.setOd(od);
            }

            final LinkedList<Mod> modList = new LinkedList<>();

            for (JsonElement jsonElement : JsonParser.parseString(mods.toJson().toString()).getAsJsonArray().asList()) {
                modList.add(GSON.fromJson(jsonElement, Mod.class));
            }

            diffSpec.setMods(modList);
            diffSpec.setMaxCombo(scoreState.max_combo);

            return diffSpec;
        }
    }

    protected void getBeatmapsetRef(@NotNull Context context) {
        final String of = context.queryParam("of");
        final String u = context.queryParam("u");
        final String iStr = context.queryParam("i");

        if (u == null || iStr == null || of == null) {
            context.status(400).result(Response.error("Invalid query parameter! Missing u", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        if (!((of.startsWith("rs") || of.startsWith("bo")) && isInteger(iStr))) {
            context.status(400).result(Response.error(
                    "Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString()
            );
            return;
        }

        final int num = Integer.parseInt(iStr);

        final Optional<List<Score>> rs = executor.enqueue(() -> OsuAPI.getUserScores(tokenManager.getTokenData(), u, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, num, false));

        if (rs.isEmpty() || rs.get().size() < num) {
            context.status(400).result(Response.error("No scores found for user!", ErrorCode.NO_SCORE_FOUND).toString());
            return;
        }

        final var beatmapsetId = rs.get().getLast().getBeatmapset().getId();
        executor.enqueue(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), String.valueOf(beatmapsetId)))
                .ifPresentOrElse(beatmapset -> {
                    try {
                        final byte[] bytes = renderer.renderBeatmapset(beatmapset);
                        context.status(200).result(bytes);
                    } catch (Exception e) {
                        context.status(500).result(Response.error("An error occurred while processing the request!", ErrorCode.BEATMAPSET_FETCH_FAILED).toString());
                        LOG.error("An error occurred while processing request: {}", context.queryString(), e);
                    }
                }, () -> context.status(400).result(Response.error("No beatmapset found", ErrorCode.NO_BEATMAPSET_FOUND).toString()));
    }

    private void getBeatmapsetOfMap(@NotNull Context context) {
        final String m = context.queryParam("m");

        if (m == null) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        final var beatmapsetOptional = executor.enqueue(() -> OsuAPI.getBeatmapsetFromBeatmap(tokenManager.getTokenData(), m));

        if (beatmapsetOptional.isEmpty()) {
            context.status(400).result(Response.error("No beatmapset found", ErrorCode.NO_BEATMAPSET_FOUND).toString());
            return;
        }

        final Beatmapset beatmapset = beatmapsetOptional.get();
        final byte[] bytes = renderer.renderBeatmapset(beatmapset);

        context.status(200).result(bytes);
    }

    protected void getBeatmapset(@NotNull Context context) {
        if (context.queryParam("of") != null) {
            getBeatmapsetRef(context);
        } else if (context.queryParam("m") != null) {
            getBeatmapsetOfMap(context);
        } else {
            getBeatmapsetOfId(context);
        }
    }

    private void getBeatmapsetOfId(@NotNull Context context) {
        final String ms = context.queryParam("ms");

        if (ms == null || !isInteger(ms)) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        final var beatmapsetOptional = executor.enqueue(() -> OsuAPI.getBeatmapset(tokenManager.getTokenData(), ms));

        if (beatmapsetOptional.isEmpty()) {
            context.status(400).result(Response.error("No beatmapset found", ErrorCode.NO_BEATMAPSET_FOUND).toString());
            return;
        }

        final Beatmapset beatmapset = beatmapsetOptional.get();
        final byte[] bytes = renderer.renderBeatmapset(beatmapset);

        context.status(200).result(bytes);
    }

    protected void bypassRequest(@NotNull Context context) {
        executor.enqueue(() -> OsuAPI.byPassRequest(tokenManager.getTokenData(), context.queryString()))
                .ifPresent(r -> context.status(200).result(new Response(true, "Success", r).toString()));
    }

    protected void getServerStatus(@NotNull Context context) {
        LOG.info("{} - status", context.ip());
        context.status(200).result(new Response(true, "Server is running!", null).toString());
    }

    protected void getRecentScores(@NotNull Context context) throws Exception {
        final String u = context.queryParam("u");
        final String n = context.queryParam("n");

        if (u == null || n == null || !isInteger(n)) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
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
            context.status(400).result(Response.error("No user found", ErrorCode.NO_USER_FOUND).toString());
            return;
        }

        final byte[] bytes = renderer.renderScores(user.get(), scores, ScoreType.RECENT);

        context.status(200).result(bytes);
    }

    protected void getMultiplayerRooms(@NotNull Context context) {
        final var roomsOptional = executor.enqueue(() -> OsuAPI.getRooms(tokenManager.getTokenData()));
        if (roomsOptional.isEmpty()) {
            context.status(400).result(Response.error("No rooms found", ErrorCode.NO_ROOM_FOUND).toString());
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

    protected void getDaily(@NotNull Context context) {
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

    protected void getBestOfN(@NotNull Context context) {
        final String u = context.queryParam("u");
        final String n = context.queryParam("n");

        if (u == null || n == null || !isInteger(n)) {
            context.status(400)
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
            context.status(400).result(Response.error("No user found", ErrorCode.NO_USER_FOUND).toString());
            return;
        }

        final var user = executor.enqueue(() -> OsuAPI.getUser(tokenManager.getTokenData(), u));

        if (user.isEmpty()) {
            context.status(400).result(Response.error("No user found", ErrorCode.NO_USER_FOUND).toString());
            return;
        }

        final byte[] bytes = renderer.renderScores(user.get(), scores.get(), ScoreType.BEST);
        context.status(200).result(bytes);
    }

    @Override
    public void close() {
        executor.close();
        renderer.close();
    }

    protected void queueReplayRenderOfRef(@NotNull Context context) throws Exception {
        final String of = context.queryParam("of");
        final String u = context.queryParam("u");
        final String iStr = context.queryParam("i");

        if (u == null || iStr == null || of == null) {
            context.status(400).result(Response.error("Invalid query parameter! Missing u", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        if (!((of.startsWith("rs") || of.startsWith("bo")) && isInteger(iStr))) {
            context.status(400).result(Response.error(
                    "Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString()
            );
            return;
        }

        final int num = Integer.parseInt(iStr);

        final Optional<List<Score>> rs = executor.enqueue(() -> OsuAPI.getUserScores(tokenManager.getTokenData(), u, of.equals("rs") ? ScoreType.RECENT : ScoreType.BEST, num, false));

        if (rs.isEmpty() || rs.get().size() < num) {
            context.status(400).result(Response.error("No scores found for user!", ErrorCode.NO_SCORE_FOUND).toString());
            return;
        }

        final Score score = rs.get().get(num - 1);

        if (!score.getHasReplay()) {
            context.status(400).result(Response.error("Replay unavailable!", ErrorCode.REPLAY_UNAVAILABLE).toString());
            return;
        }

        cacheService.cacheBeatmapset(String.valueOf(score.getBeatmapset().getId()));

        final Path replay = cacheService.getReplay(tokenManager.getTokenData(), String.valueOf(score.getId()));

        final int queueSize = replayRenderService.getQueueSize() + 1;
        final String jobId = replayRenderService.queueRender(replay);

        context.status(202).result(
                new Response(
                        true,
                        "Replay render queued!",
                        GSON.toJsonTree(Map.of(
                                "status", "queued",
                                "position", queueSize,
                                "id", jobId
                        ))
                ).toString()
        );
    }

    protected void queueReplayRender(@NotNull Context context) throws Exception {
        if (context.queryParam("of") != null) {
            queueReplayRenderOfRef(context);
        } else {
            queueReplayRenderOfId(context);
        }
    }

    private void queueReplayRenderOfId(@NotNull Context context) throws Exception {
        final String s = context.queryParam("s");

        if (s == null) {
            context.status(400).result(Response.error("Invalid query parameter!", ErrorCode.ILLEGAL_ARGUMENT).toString());
            return;
        }

        final Optional<Score> enqueue = executor.enqueue(() -> OsuAPI.getScore(tokenManager.getTokenData(), s));

        if (enqueue.isEmpty()) {
            context.status(400).result(Response.error("No score found!", ErrorCode.NO_SCORE_FOUND).toString());
            return;
        }

        final Score score = enqueue.get();

        if (!score.getHasReplay()) {
            context.status(400).result(Response.error("Replay unavailable!", ErrorCode.REPLAY_UNAVAILABLE).toString());
            return;
        }

        cacheService.cacheBeatmapset(String.valueOf(score.getBeatmapset().getId()));

        final Path replay = cacheService.getReplay(tokenManager.getTokenData(), s);

        final int queueSize = replayRenderService.getQueueSize() + 1;
        final String jobId = replayRenderService.queueRender(replay);

        context.status(202).result(
                new Response(
                        true,
                        "Replay render queued!",
                        GSON.toJsonTree(Map.of(
                                "status", "queued",
                                "position", queueSize,
                                "id", jobId
                        ))
                ).toString()
        );
    }

    public void getReplayRenderStatus(@NotNull Context context) {
        String jobId = context.pathParam("jobId");
        String status = replayRenderService.getJobStatus().getOrDefault(jobId, "unknown");

        switch (status) {
            case "done" -> context.status(200).result(new Response(true, "Render complete!",
                    GSON.toJsonTree(Map.of(
                            "status", "done"
                    ))).toString());
            case "failed" -> context.status(500).result(new Response(false, "Render failed",
                    GSON.toJsonTree(Map.of(
                            "status", "failed",
                            "id", jobId
                    ))).toString());
            case "queued", "rendering" -> context.status(202).result(new Response(false, "Render waiting",
                    GSON.toJsonTree(Map.of(
                            "status", status,
                            "id", jobId
                    ))).toString());
            default -> context.status(400).result(new Response(false, "Job not found", null).toString());
        }
    }

    public void getReplayRenderResult(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");
        Path video = replayRenderService.getJobResults().get(jobId);

        if (video != null && Files.exists(video)) {
            context.writeSeekableStream(Files.newInputStream(video), "video/mp4");
        } else {
            context.status(404).result("Video expired or not found");
        }
    }

    public void deleteReplayRenderResult(@NotNull Context context) throws IOException {
        String jobId = context.pathParam("jobId");

        Path video = replayRenderService.getJobResults().get(jobId);

        replayRenderService.getJobStatus().remove(jobId);
        replayRenderService.getJobResults().remove(jobId);

        if (video != null && Files.exists(video)) {
            Files.deleteIfExists(video);
        }

        context.status(200).result("Job cleaned up successfully");
    }
}
