package xyz.zcraft.service;

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
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.DiffSpec;
import xyz.zcraft.model.score.Placement;
import xyz.zcraft.model.score.Score;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.model.user.User;
import xyz.zcraft.model.user.UserExtended;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ScoreRenderService {
    private static final Logger LOG = LogManager.getLogger(ScoreRenderService.class);
    private static Playwright playwright;
    private static Browser browser;
    private static TemplateEngine templateEngine;

    public ScoreRenderService() {
        LOG.info("Initializing template resolver");
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/template/"); // Looks in src/main/resources/templates/
        resolver.setSuffix(".html");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        LOG.info("Setting up playwright");

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();

            // Load your HTML or navigate to your page
            page.setContent("<html><head><style>body{font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;}</style></head><body>Dandelions & abcdefg</body></html>");

            // Evaluate JS to get the computed font-family of the body (or any specific element)
            String fontFamily = (String) page.locator("body")
                    .evaluate("element => window.getComputedStyle(element).fontFamily");

            LOG.warn("The computed font family is: {}", fontFamily);

            browser.close();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            browser.close();
            playwright.close();
        }));
    }

    public byte[] renderScores(UserExtended user, List<Score> scores, ScoreType type) {
        Context ctx = new Context();
        ctx.setVariable("user", user);
        ctx.setVariable("scores", scores);
        ctx.setVariable("type", switch (type) {
            case BEST -> "Best of " + scores.size() + " Scores";
            case RECENT -> "Most recent " +  scores.size() + " Scores";
        });
        ctx.setVariable("change", user.getScoreChange());
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("scores", ctx);

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

    public byte[] renderPK(BeatmapExtended map, List<Placement> placements, double ppMax) {
        Context ctx = new Context();
        ctx.setVariable("beatmap", map);
        ctx.setVariable("placements", placements);
        ctx.setVariable("ppMax", ppMax);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("pk", ctx);

        Page page = browser.newPage();

        page.setViewportSize(760, 400);

        page.setContent(finalHtml);

        page.waitForLoadState(LoadState.NETWORKIDLE);

        byte[] screenshotBytes = page.screenshot(
                new Page.ScreenshotOptions().setFullPage(true)
        );

        page.close();

        return screenshotBytes;
    }

    public byte[] renderLeaderboard(List<User> users) {
        Context ctx = new Context();
        ctx.setVariable("users", users);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("leaderboard", ctx);

        Page page = browser.newPage();

        page.setViewportSize(760, 400);

        page.setContent(finalHtml);

        page.waitForLoadState(LoadState.NETWORKIDLE);

        byte[] screenshotBytes = page.screenshot(
                new Page.ScreenshotOptions().setFullPage(true)
        );

        page.close();

        return screenshotBytes;
    }

    public byte[] renderBeatmap(BeatmapExtended map, DiffSpec spec) {
        Context ctx = new Context();
        ctx.setVariable("beatmap", map);
        ctx.setVariable("diff", spec);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("beatmap", ctx);

        Page page = browser.newPage();

        page.setViewportSize(960, 760);

        page.setContent(finalHtml);

        page.waitForLoadState(LoadState.NETWORKIDLE);

        byte[] screenshotBytes = page.screenshot(
                new Page.ScreenshotOptions().setFullPage(true)
        );

        page.close();

        return screenshotBytes;
    }

    public byte[] renderFonts() {
        String finalHtml = templateEngine.process("fonts", new Context());

        Page page = browser.newPage();

        page.setViewportSize(960, 760);

        page.setContent(finalHtml);

        page.waitForLoadState(LoadState.NETWORKIDLE);

        byte[] screenshotBytes = page.screenshot(
                new Page.ScreenshotOptions().setFullPage(true)
        );

        page.close();

        return screenshotBytes;
    }
}
