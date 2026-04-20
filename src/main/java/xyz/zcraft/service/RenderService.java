package xyz.zcraft.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import xyz.zcraft.model.beatmap.Beatmap;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.Beatmapset;
import xyz.zcraft.model.beatmap.DiffSpec;
import xyz.zcraft.model.score.Placement;
import xyz.zcraft.model.score.Score;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.model.user.User;
import xyz.zcraft.model.user.UserExtended;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class RenderService implements AutoCloseable{
    private static final Logger LOG = LogManager.getLogger(RenderService.class);
    private final CacheService cacheService;
    private final LinkedList<AutoCloseable>  resources = new LinkedList<>();
    private final ThreadLocal<Playwright> playwrightLocal = ThreadLocal.withInitial(() -> {
        LOG.info("Starting new Playwright instance for Thread: {}", Thread.currentThread().getName());

        final Playwright playwright = Playwright.create();
        resources.add(playwright);

        LOG.info("Playwright instance created for Thread: {}", Thread.currentThread().getName());

        return playwright;
    });
    private final ThreadLocal<Browser> browserLocal = ThreadLocal.withInitial(() -> {
        LOG.info("Launching Chromium for Thread: {}", Thread.currentThread().getName());

        final Browser browser = playwrightLocal.get().chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

        LOG.info("Chromium launched for Thread: {}", Thread.currentThread().getName());

        return browser;
    });
    private final TemplateEngine templateEngine;

    public RenderService(CacheService cacheService) {
        this.cacheService = cacheService;

        LOG.info("Initializing template resolver");
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/template/"); // Looks in src/main/resources/template/
        resolver.setSuffix(".html");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        LOG.info("Setting up playwright");
    }

    private byte[] takeScreenshot(String html) {
        Browser browser = browserLocal.get();

        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions());
             Page page = context.newPage()) {
            page.route("http://ostella-cache/**", route -> {
                String url = route.request().url();
                String filename = url.substring(url.lastIndexOf("/") + 1);
                java.nio.file.Path imagePath = cacheService.getImagePathFromFilename(filename);
                route.fulfill(new Route.FulfillOptions().setPath(imagePath));
            });

            page.setContent(html);
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForFunction("() => Array.from(document.images).every(img => img.complete)");
            return page.locator("body").screenshot();
        }
    }

    public byte[] renderScores(UserExtended user, List<Score> scores, ScoreType type) {
        Context ctx = new Context();
        ctx.setVariable("cache", cacheService);
        ctx.setVariable("user", user);
        ctx.setVariable("scores", scores);
        ctx.setVariable("type", switch (type) {
            case BEST -> "Best of " + scores.size() + " Scores";
            case RECENT -> "Most recent " + scores.size() + " Scores";
        });
        ctx.setVariable("change", user.getScoreChange());
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("scores", ctx);

        return takeScreenshot(finalHtml);
    }

    public byte[] renderPK(BeatmapExtended map, List<Placement> placements, double ppMax) {
        Context ctx = new Context();
        ctx.setVariable("cache", cacheService);
        ctx.setVariable("beatmap", map);
        ctx.setVariable("placements", placements);
        ctx.setVariable("ppMax", ppMax);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("pk", ctx);

        return takeScreenshot(finalHtml);
    }

    public byte[] renderLeaderboard(List<User> users) {
        Context ctx = new Context();
        ctx.setVariable("cache", cacheService);
        ctx.setVariable("users", users);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("leaderboard", ctx);

        return takeScreenshot(finalHtml);
    }

    public byte[] renderBeatmap(BeatmapExtended map, DiffSpec spec) {
        Context ctx = new Context();
        ctx.setVariable("cache", cacheService);
        ctx.setVariable("beatmap", map);
        ctx.setVariable("diff", spec);
        ctx.setVariable("diff", spec);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("beatmap", ctx);

        return takeScreenshot(finalHtml);
    }

    public byte[] renderScore(Score score, DiffSpec spec) {
        Context ctx = new Context();
        ctx.setVariable("cache", cacheService);
        ctx.setVariable("score", score);
        ctx.setVariable("diff", spec);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("ascore", ctx);

        return takeScreenshot(finalHtml);
    }

    public byte[] renderBeatmapset(Beatmapset beatmapset) {
        beatmapset.getBeatmaps().sort(Comparator.comparingDouble(Beatmap::getDifficultyRating));

        Context ctx = new Context();
        ctx.setVariable("cache", cacheService);
        ctx.setVariable("beatmapset", beatmapset);
        ctx.setVariable("time", Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String finalHtml = templateEngine.process("beatmapset", ctx);

        return takeScreenshot(finalHtml);
    }

    @Override
    public void close() {
        LOG.info("Closing RenderService resources");
        for (AutoCloseable resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                LOG.error("Failed to close resource: {}", resource.getClass().getSimpleName(), e);
            }
        }
    }
}
