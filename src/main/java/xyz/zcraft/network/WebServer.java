package xyz.zcraft.network;

import io.javalin.Javalin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import xyz.zcraft.config.AppConfig;
import xyz.zcraft.util.TokenManager;

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
                    .get("/bo", router::getBestOfNAsync)
                    .get("/daily", router::getDailyAsync)
                    .get("/mp", router::getMultiplayerRoomsAsync)
                    .get("/rs", router::getRecentScoresAsync)
                    .get("/m", router::getBeatmapAsync)
                    .get("/ms", router::getBeatmapsetAsync)
                    .get("/sms", router::searchBeatmapSetAsync)
                    .get("/s", router::getScoreAsync)
                    .get("/pk", router::getPKAsync)
                    .get("/lb", router::getLeaderBoardAsync)
                    .get("/status", router::getServerStatusAsync);


            cfg.routes.get("/replay/status", router::getReplayRenderOverview);
            if(conf.replayRender().enabled()) {
                cfg.routes.get("/replay/render", router::queueReplayRenderAsync)
                        .get("/replay/showcase", router::queueShowcaseRenderAsync)
                        .get("/replay/status/{jobId}", router::getReplayRenderStatus)
                        .get("/replay/video/{jobId}", router::getReplayRenderResultStream)
                        .get("/replay/video/{jobId}/replay.mp4", router::getReplayRenderResultFile)
                        .delete("/replay/video/{jobId}", router::deleteReplayRenderResult);
            } else {
                LOG.info("No danser path found, replay rendering will be disabled.");
            }

            if (conf.ostella().debugMode()) {
                LOG.warn("/bypass endpoint is enabled in debug mode! To prevent security risks, please disable debug mode in production environment.");
                cfg.routes.get("/debug/bypass", router::bypassRequest);
            }

            cfg.routes
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
