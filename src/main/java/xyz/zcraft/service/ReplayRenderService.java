package xyz.zcraft.service;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReplayRenderService {
    private static final Logger LOG = LogManager.getLogger(ReplayRenderService.class);
    private final Path danserPath;
    private final Path songPath;
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    //  "queued", "rendering", "done", "failed"
    @Getter
    private final ConcurrentHashMap<String, String> jobStatus = new ConcurrentHashMap<>();
    @Getter
    private final ConcurrentHashMap<String, Path> jobResults = new ConcurrentHashMap<>();

    public ReplayRenderService(Path danserPath, Path songPath) {
        this.danserPath = danserPath;
        this.songPath = songPath;

        try (var executor = Executors.newSingleThreadScheduledExecutor()) {
            executor.scheduleAtFixedRate(() -> {
                try {
                    long fifteenMinutesAgo = System.currentTimeMillis() - (15 * 60 * 1000);

                    Iterator<Map.Entry<String, Path>> iterator = jobResults.entrySet().iterator();

                    while (iterator.hasNext()) {
                        Map.Entry<String, Path> entry = iterator.next();
                        String jobId = entry.getKey();
                        Path video = entry.getValue();

                        if (video != null && Files.getLastModifiedTime(video).toMillis() < fifteenMinutesAgo) {
                            Files.deleteIfExists(video);
                            jobStatus.remove(jobId);
                            iterator.remove();
                            LOG.info("Garbage Collector wiped stale job: {}", jobId);
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Error during garbage collection of rendered videos", e);
                }
            }, 5, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String queueRender(Path osrPath) {
        final String jobId = UUID.randomUUID().toString();
        jobStatus.put(jobId, "queued");
        executor.submit(() -> render(osrPath, jobId));
        return jobId;
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    private void render(Path osrPath, String jobId) {
        jobStatus.put(jobId, "rendering");
        try {
            final String fileName = "highlight_" + System.currentTimeMillis();
            String safeSongPath = songPath.toAbsolutePath().toString().replace("\\", "/");

            ProcessBuilder builder = new ProcessBuilder(
                    danserPath.toAbsolutePath().toString(),
                    "-sPatch={\\\"General\\\":" +
                            "{\\\"OsuSongsDir\\\":\\\"" + safeSongPath + "\\\"}" +
                            ",\\\"Recording\\\":" +
                            "{\\\"FrameWidth\\\":960,\\\"FrameHeight\\\":540,\\\"FPS\\\":30}" +
                            "}",
                    "-noupdatecheck",
                    "-quickstart",
                    "-record",
                    "-replay=" + osrPath.toAbsolutePath(),
                    "-out=" + fileName
            );

            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            Process process = builder.start();

            boolean finished = process.waitFor(3, TimeUnit.MINUTES);
            if (!finished) {
                jobStatus.put(jobId, "failed");
                process.destroyForcibly();
                LOG.error("Danser timed out and was killed.");
                return;
            }

            LOG.info("Danser finished rendering video: {}", fileName + ".mp4");

            jobStatus.put(jobId, "done");
            final Path video = danserPath.resolve("..", "videos", fileName + ".mp4").normalize().toAbsolutePath();
            jobResults.put(jobId, video);
        } catch (Exception e) {
            jobStatus.put(jobId, "failed");
            LOG.error("Danser failed to render video", e);
        }
    }
}
