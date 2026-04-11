package xyz.zcraft;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.network.WebServer;
import xyz.zcraft.util.Config;
import xyz.zcraft.util.TokenManager;

public class oStella {
    private static final Logger LOG = LogManager.getLogger(oStella.class);

    private static WebServer webServer;
    private static TokenManager tokenManager;

    @Getter
    private static Config conf;

    static void main() {
        LOG.info("Reading .env");

        try {
            conf = Config.fromEnv();
        } catch (IllegalStateException e) {
            LOG.error("Invalid configuration! Please check your .env file.");
            System.exit(1);
            return;
        }

        if (conf.debug()) {
            LOG.warn("Debug mode is enabled! This may cause security and performance issues. Please disable debug mode in production environment.");
        }

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
