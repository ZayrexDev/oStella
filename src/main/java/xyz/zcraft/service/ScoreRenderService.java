package xyz.zcraft.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.*;
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
            case RECENT -> "Most recent " + scores.size() + " Scores";
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

        // 1. Create a CDP Session attached to the current page
        CDPSession session = page.context().newCDPSession(page);

        // Enable the required CDP domains
        session.send("DOM.enable");
        session.send("CSS.enable");

        // 2. Get the root document node ID
        JsonObject docResult = session.send("DOM.getDocument").getAsJsonObject();
        int rootNodeId = docResult.getAsJsonObject("root").get("nodeId").getAsInt();

        // 3. Find the specific node you want to check (e.g., our <h1> tag)
        JsonObject q = new JsonObject();
        q.addProperty("nodeId", rootNodeId);
        q.addProperty("selector", "#song-title");
        JsonObject queryResult = session.send("DOM.querySelector", q).getAsJsonObject();
        int targetNodeId = queryResult.get("nodeId").getAsInt();

        // 4. Ask the browser engine which physical font it actually used for this node
        JsonObject f = new JsonObject();
        f.addProperty("nodeId", targetNodeId);
        JsonObject fontResult = session.send("CSS.getPlatformFontsForNode", f).getAsJsonObject();

        // 5. Parse and print the results
        JsonArray fonts = fontResult.getAsJsonArray("fonts");
        System.out.println("Physical fonts actually used by the rendering engine:");

        for (int i = 0; i < fonts.size(); i++) {
            JsonObject font = fonts.get(i).getAsJsonObject();
            String familyName = font.get("familyName").getAsString();
            boolean isCustomFont = font.get("isCustomFont").getAsBoolean();
            int glyphCount = font.get("glyphCount").getAsInt();

            LOG.warn("- Font Family: {}", familyName);
            LOG.warn("  Is Web Font: {}", isCustomFont);
            LOG.warn("  Glyphs Rendered: {}", glyphCount);
        }

        browser.close();

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
