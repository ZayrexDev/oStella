package xyz.zcraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.network.WebServer;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.TokenManager;

import static xyz.zcraft.util.FormatUtil.isInteger;

public class oStella {
    private static final Logger LOG = LogManager.getLogger(oStella.class);

    private static WebServer webServer;
    private static TokenManager tokenManager;

    static void main() {
        LOG.info("Reading .env");

        final Config conf = Config.fromEnv();

        if (!isInteger(conf.clientId(), conf.port(), conf.maxThreads(), conf.delay())) {
            LOG.error("Invalid configuration! Please check your .env file.");
            System.exit(1);
        }

        LOG.info("Loaded id={}, secret={}, port={}",
                conf.clientId(),
                conf.clientSecret().substring(0, 4) + "****",
                conf.port()
        );

        LOG.info("Authorizing...");

        tokenManager = new TokenManager(conf);

        LOG.info("Starting web server");

        webServer = new WebServer(conf, tokenManager);
        webServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Stopping web server");
            tokenManager.close();
            webServer.close();
        }));

        LOG.info("Web server ready, waiting for requests");
    }
}
