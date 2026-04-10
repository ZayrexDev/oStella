package xyz.zcraft;

import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.data.Score;
import xyz.zcraft.data.TokenData;
import xyz.zcraft.data.UserExtended;
import xyz.zcraft.util.AsyncHelper;
import xyz.zcraft.util.NetworkHelper;
import xyz.zcraft.util.ScoreRenderer;

import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;

import static xyz.zcraft.util.FormatUtil.isInteger;

public class oStella {
    private static final Logger LOG = LogManager.getLogger(oStella.class);
    private static final Timer timer = new Timer();
    private static Config conf;
    private static ScoreRenderer renderer = null;
    private static TokenData tokenData;

    static void main() {
        LOG.info("Reading .env");

        final Dotenv dotenv = Dotenv.load();
        conf = new Config(
                dotenv.get("OSU_CLIENT_ID"), dotenv.get("OSU_CLIENT_SECRET"),
                dotenv.get("OSU_PORT"), dotenv.get("OSU_PATH"),
                dotenv.get("OSU_MAX_THREADS"), dotenv.get("OSU_DELAY_MILLIS")
        );

        if (!isInteger(conf.clientId(), conf.port(), conf.maxThreads(), conf.delay())) {
            LOG.error("Invalid configuration! Please check your .env file.");
            System.exit(1);
        }

        LOG.info("Loaded id={}, secret={}, port={}, path={}",
                conf.clientId(),
                conf.clientSecret().substring(0, 4) + "****",
                conf.port(),
                conf.path()
        );

        LOG.info("Authorizing...");

        renewToken();

        LOG.info("Setting up renderer");

        renderer = new ScoreRenderer();

        LOG.info("Setting up thread pool");

        final AsyncHelper asyncHelper = new AsyncHelper(Integer.parseInt(conf.maxThreads()));

        LOG.info("Starting web server");

        final Javalin server = Javalin.create(cfg -> cfg.routes.get(conf.path(), ctx -> {
            LOG.info("Incoming request from {} - {}", ctx.ip(), ctx.queryParam("id"));

            if (ctx.queryParam("id") == null) {
                ctx.status(400).result("Missing id query parameter!");
                return;
            }

            final CompletableFuture<byte[]> completableFuture = asyncHelper.runAsync(() -> {
                try {
                    Thread.sleep(Integer.parseInt(conf.delay()));
                } catch (InterruptedException e) {
                    LOG.error("Interrupted while waiting for request!", e);
                }
                final List<Score> scores = NetworkHelper.getUserScores(ctx.queryParam("id"), tokenData, 40);
                final UserExtended user = NetworkHelper.getUser(ctx.queryParam("id"), tokenData);

                return renderer.render(user, scores);
            });

            ctx.contentType("img/png");
            ctx.result(completableFuture.get());
        })).start(Integer.parseInt(conf.port()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down application");
            timer.cancel();

            LOG.info("Shutting down server");
            server.stop();

            LOG.info("Shutting down thread pool");
            asyncHelper.close();
        }));

        LOG.info("Web server ready, waiting for requests");
    }

    private static void renewToken() {
        if (tokenData == null || tokenData.token() == null ||
                System.currentTimeMillis() - tokenData.tokenGrantTime() >= tokenData.expiresIn() * 1000) {
            tokenData = NetworkHelper.getToken(conf);
            LOG.info("Token renewed, expires in {}", tokenData.expiresIn());

            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    LOG.info("Preparing to renew token");
                    renewToken();
                }
            }, (tokenData.expiresIn() - 60) * 1000);
        }
    }
}
