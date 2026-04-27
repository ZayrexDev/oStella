package xyz.zcraft;

import desu.life.RosuFFI;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import xyz.zcraft.config.AppConfig;
import xyz.zcraft.config.ConfigLoader;
import xyz.zcraft.network.WebServer;
import xyz.zcraft.util.TokenManager;

import java.io.IOException;

public class oStella {
    private static final Logger LOG = LogManager.getLogger(oStella.class);

    private static WebServer webServer;
    private static TokenManager tokenManager;

    @Getter
    private static AppConfig conf;

    static void main() {
        LOG.info("Reading config.yml");

        if(!ConfigLoader.configExists()) {
            LOG.warn("Config file does not exist, copying default config. Please check your config file.");
            try {
                ConfigLoader.copyDefaultConfig();
            } catch (IOException e) {
                LOG.error("Failed to copy default config", e);
            }

            System.exit(0);
        }

        try {
            conf = ConfigLoader.loadConfig();
        } catch (Exception e) {
            LOG.error("Invalid configuration! Please check your config.yml file.");
            System.exit(1);
            return;
        }

        if (conf.ostella().debugMode()) {
            Configurator.setRootLevel(org.apache.logging.log4j.Level.DEBUG);
            LOG.warn("Debug mode is enabled! This may cause security and performance issues. Please disable debug mode in production environment.");
        }

        LOG.info("Initializing RosuFFI, you may ignore the warnings below.");

        try {
            new RosuFFI.Beatmap(new byte[0]).close();
        } catch (RosuFFI.FFIException e) {
            LOG.error("Error while initializing RosuFFI", e);
        }

        LOG.info("Authorizing...");

        tokenManager = new TokenManager(conf);

        LOG.info("Starting web server");

        try {
            webServer = new WebServer(conf, tokenManager);
            webServer.start();
        } catch (IOException e) {
            LOG.error("Failed to start web server", e);
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Stopping web server");
            tokenManager.close();
            webServer.close();
        }));

        LOG.info("Web server ready, waiting for requests");
    }
}
