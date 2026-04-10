package xyz.zcraft.util;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import xyz.zcraft.data.Score;
import xyz.zcraft.data.UserExtended;

import java.util.List;

public class ScoreRenderer {
    private static final Logger LOG = LogManager.getLogger(ScoreRenderer.class);
    private static Playwright playwright;
    private static Browser browser;
    private static TemplateEngine templateEngine;

    public ScoreRenderer() {
        LOG.info("Initializing template resolver");
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/templates/"); // Looks in src/main/resources/templates/
        resolver.setSuffix(".html");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        LOG.info("Setting up playwright");

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            browser.close();
            playwright.close();
        }));
    }

    public byte[] render(UserExtended user, List<Score> scores) {
        Context ctx = new Context();
        ctx.setVariable("user", user);
        ctx.setVariable("scores", scores);

        String finalHtml = templateEngine.process("stat", ctx);

        Page page = browser.newPage();

        page.setViewportSize(1400, 1000);

        page.setContent(finalHtml);

        page.waitForLoadState(LoadState.NETWORKIDLE);

        byte[] screenshotBytes = page.screenshot(
                new Page.ScreenshotOptions().setFullPage(true)
        );

        page.close();

        return screenshotBytes;
    }
}
