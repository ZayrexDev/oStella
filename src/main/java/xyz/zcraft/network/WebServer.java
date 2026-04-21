package xyz.zcraft.network;

import io.javalin.Javalin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.TokenManager;

import java.io.Closeable;
import java.io.IOException;

public class WebServer implements Closeable {
    private static final Logger LOG = LogManager.getLogger(WebServer.class);

    private final Config conf;
    private final Javalin app;
    private final Router router;

    public WebServer(Config conf, TokenManager tokenManager) throws IOException {
        this.conf = conf;
        this.router = new Router(conf, tokenManager);
        app = Javalin.create(cfg -> {
            final QueuedThreadPool threadPool = new QueuedThreadPool(Math.max(5, conf.maxThreads() + 3), 2, 60000);
            threadPool.setName("ServPool");
            cfg.jetty.threadPool = threadPool;

            cfg.routes
                    .get("/bo", router::getBestOfN)
                    .get("/daily", router::getDaily)
                    .get("/mp", router::getMultiplayerRooms)
                    .get("/rs", router::getRecentScores)
                    .get("/m", router::getBeatmap)
                    .get("/ms", router::getBeatmapset)
                    .get("/sms", router::searchBeatmapSet)
                    .get("/s", router::getScore)
                    .get("/pk", router::getPK)
                    .get("/lb", router::getLeaderBoard)
                    .get("/status", router::getServerStatus);

            if(conf.danserPath() != null) {
                cfg.routes.get("/replay/render", router::queueReplayRender)
                        .get("/replay/status/{jobId}", router::getReplayRenderStatus)
                        .get("/replay/video/{jobId}", router::getReplayRenderResult)
                        .delete("/replay/video/{jobId}", router::deleteReplayRenderResult);
            } else {
                LOG.info("No danser path found, replay rendering will be disabled.");
            }

            if (conf.debug()) {
                LOG.warn("/bypass endpoint is enabled in debug mode! To prevent security risks, please disable debug mode in production environment.");
                cfg.routes.get("/debug/bypass", router::bypassRequest);
            }

            cfg.routes
                    .before(ctx -> LOG.info("{} - {} {} {}", ctx.ip(), ctx.method(), ctx.path(), ctx.queryString()))
                    .exception(Exception.class, (e, ctx) -> {
                        ctx.status(500).result(new Response(false, "An error occurred while processing the request!", null).toString());
                        LOG.error("An error occurred while processing request: {}", ctx.queryString(), e);
                    });
        });
    }

    public void start() {
        app.start(conf.port());
        LOG.info("Started web server on port {}", conf.port());
    }

    @Override
    public void close() {
        app.stop();
        router.close();
    }
}
