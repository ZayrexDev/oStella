package xyz.zcraft.util;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.config.AppConfig;
import xyz.zcraft.model.TokenData;
import xyz.zcraft.network.OsuAPI;

import java.util.Timer;

public class TokenManager {
    private static final Logger LOG = LogManager.getLogger(TokenManager.class);

    private final Timer timer = new Timer();
    private final AppConfig conf;
    @Getter
    private TokenData tokenData;

    public TokenManager(AppConfig conf) {
        this.conf = conf;
        renewToken();
    }

    public void close() {
        timer.cancel();
    }

    private void renewToken() {
        tokenData = OsuAPI.getToken(conf);
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
