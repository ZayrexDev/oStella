package xyz.zcraft.ostella.network;

import io.javalin.Javalin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import xyz.zcraft.ostella.config.AppConfig;
import xyz.zcraft.ostella.util.TokenManager;

import java.io.Closeable;
import java.io.IOException;

public class WebServer implements Closeable {
    private static final Logger LOG = LogManager.getLogger(WebServer.class);

    private final AppConfig conf;
    private final Javalin app;
    private final Router router;

    public WebServer(AppConfig conf, TokenManager tokenManager) throws IOException {
        this.conf = conf;
        this.router = new Router(conf, tokenManager);
        app = Javalin.create(cfg -> {
            final QueuedThreadPool threadPool = new QueuedThreadPool(
                    Math.max(8, conf.webserver().maxThreads() + 3),
                    Math.max(2, conf.webserver().minThreads()),
                    Math.max(1000, conf.webserver().idleTimeout())
            );
            threadPool.setName("ServPool");
            cfg.jetty.threadPool = threadPool;

            cfg.routes.before(ctx -> LOG.debug("{} {} {}", ctx.method(), ctx.path(), ctx.queryString()));

            cfg.routes
                    .get("/bestof", router::getBestOfN)
                    .get("/daily", router::getDaily)
                    .get("/mp", router::getCurrentRoom)
                    .get("/mp/current", router::getCurrentRoomItem)
                    .get("/recent", router::getRecentScores)
                    .get("/lookup/beatmap", router.beatmapController::lookupBeatmap)
                    .get("/lookup/beatmapset", router.beatmapsetController::lookupBeatmapset)
                    .get("/lookup/score", router.scoreController::lookupScore)
                    .get("/beatmap/{beatmapId}", router.beatmapController::renderBeatmapById)
                    .get("/beatmapset/{beatmapsetId}", router.beatmapsetController::renderBeatmapsetById)
                    .get("/score/{scoreId}", router.scoreController::renderScoreById)
                    .get("/score/{scoreId}/analyze", router.analyzeController::renderScoreAnalysisById)
                    .post("/maplb/{beatmapId}", router.leaderboardController::getMapLeaderboard)
                    .post("/leaderboard", router.leaderboardController::getLeaderboard)
                    .get("/searchms", router::searchBeatmapSet)
                    .get("/status", router::getServerStatus)
                    .get("/friends", router::getFriends)
                    .get("/self", router::getSelf)
                    .get("/dl", router.beatmapsetController::downloadBeatmapset);

            if (conf.replayRender().enabled()) {
                cfg.routes
                        .get("/replay/status", router.replayController::getReplayRenderOverview)
                        .get("/replay/render/{scoreId}", router.replayController::queueReplayRenderById)
                        .get("/replay/showcase", router.replayController::queueShowcaseRender)
                        .get("/replay/status/{jobId}", router.replayController::getReplayRenderStatus)
                        .get("/replay/video/{jobId}", router.replayController::getReplayRenderResultStream)
                        .get("/replay/video/{jobId}/replay.mp4", router.replayController::getReplayRenderResultFile)
                        .delete("/replay/video/{jobId}", router.replayController::deleteReplayRenderResult);
            } else {
                LOG.info("Replay rendering will is disabled.");
            }

            cfg.routes
                    .exception(ApiException.class, (e, ctx) -> {
                        switch (e.getErrorCode()) {
                            case ErrorCode.NO_BEATMAP_FOUND,
                                 ErrorCode.NO_BEATMAPSET_FOUND,
                                 ErrorCode.NO_SCORE_FOUND,
                                 ErrorCode.NO_ROOM_FOUND,
                                 ErrorCode.NO_USER_FOUND -> ctx.status(404);

                            case ErrorCode.UNAUTHORIZED -> ctx.status(401);

                            case ErrorCode.ILLEGAL_ARGUMENT,
                                 ErrorCode.REPLAY_UNAVAILABLE -> ctx.status(400);

                            case ErrorCode.BEATMAP_FETCH_FAILED,
                                 ErrorCode.BEATMAPSET_FETCH_FAILED,
                                 ErrorCode.SCORE_FETCH_FAILED,
                                 ErrorCode.USER_FETCH_FAILED,
                                 ErrorCode.RENDER_QUEUE_FULL,
                                 ErrorCode.ROSU_ERROR -> ctx.status(500);

                            default -> ctx.status(500);
                        }
                        ctx.result(new Response(false, e.getMessage(), e.getErrorCode().toJson()).toString());
                        if (e.getWrappedException() != null) {
                            LOG.error("API error occurred while processing request: {} - {}", ctx.queryString(), e.getMessage(), e.getWrappedException());
                        } else {
                            LOG.error("API error occurred while processing request: {} - {}", ctx.queryString(), e.getMessage());
                        }
                    })
                    .exception(Exception.class, (e, ctx) -> {
                        ctx.status(500).result(new Response(false, "An error occurred while processing the request!", null).toString());
                        LOG.error("An error occurred while processing request: {}", ctx.queryString(), e);
                    });
        });
    }

    public void start() {
        app.start(conf.webserver().port());
        LOG.info("Started web server on port {}", conf.webserver().port());
    }

    @Override
    public void close() {
        app.stop();
        router.close();
    }
}
