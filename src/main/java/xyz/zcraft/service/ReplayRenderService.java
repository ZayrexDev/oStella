package xyz.zcraft.service;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ReplayRenderService {
    private static final Logger LOG = LogManager.getLogger(ReplayRenderService.class);
    private static final Logger DANSER_LOG = LogManager.getLogger("danser");
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

    private void consumeDanserOutput(InputStream inputStream) {
        Thread gobblerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    DANSER_LOG.info(line);
                }
            } catch (Exception e) {
                DANSER_LOG.error("Failed to read Danser stream", e);
            }
        });

        gobblerThread.setDaemon(true);
        gobblerThread.start();
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
        Path tempSettingsFile = null;
        try {
            final String fileName = "replay_" + System.currentTimeMillis();
            String safeSongPath = songPath.toAbsolutePath().toString().replace("\\", "/");

            String jsonContent = """
                    {
                        "General": {
                            "OsuSongsDir": "%s"
                        },
                        "Recording": {
                            "FrameWidth": 854,
                            "FrameHeight": 480,
                            "FPS": 30,
                            "EncoderOptions": "-crf 28 -preset ultrafast"
                        }
                    }
                    """.formatted(safeSongPath);
            Path settingsDir = danserPath.getParent().resolve("settings");
            Files.createDirectories(settingsDir);
            String tempProfileName = "ostella_temp_" + UUID.randomUUID().toString().substring(0, 8);
            tempSettingsFile = settingsDir.resolve(tempProfileName + ".json");
            Files.writeString(tempSettingsFile, jsonContent);

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

            List<String> c = new LinkedList<>();

            if (!isWindows) {
                c.add("xvfb-run");
                c.add("-a");
            }

            c.add(danserPath.toAbsolutePath().toString());
            c.add("-settings=" + tempProfileName);
            c.add("-noupdatecheck");
            c.add("-quickstart");
            c.add("-record");
            c.add("-replay=" + osrPath.toAbsolutePath());
            c.add("-out=" + fileName);

            ProcessBuilder builder = new ProcessBuilder(c);

            builder.redirectErrorStream(true);

            Process process = builder.start();

            consumeDanserOutput(process.getInputStream());

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
        } finally {
            if (tempSettingsFile != null) {
                try {
                    Files.deleteIfExists(tempSettingsFile);
                } catch (IOException e) {
                    LOG.error("Failed to delete temp settings file", e);
                }
            }
        }
    }
}
