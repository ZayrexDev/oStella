package xyz.zcraft.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplayRenderService implements Closeable {
    private static final Logger LOG = LogManager.getLogger(ReplayRenderService.class);
    private static final Logger DANSER_LOG = LogManager.getLogger("danser");
    private static final Pattern DANSER_PROGRESS_PATTERN =
            Pattern.compile("Progress: (\\d+)%, Speed: ([\\d.]+)x, ETA: (.+)");
    private final Path danserPath;
    private final Path songPath;
    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    private final ScheduledExecutorService cleanUpExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, JobProgress> jobProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Path> jobResults = new ConcurrentHashMap<>();

    public JobProgress getJobProgress(String jobId) {
        return jobProgress.getOrDefault(jobId, new JobProgress(JobStatus.UNKNOWN));
    }

    public JobStatus getJobStatus(String jobId) {
        return jobProgress.getOrDefault(jobId, new JobProgress(JobStatus.UNKNOWN)).status();
    }

    public void removeJobProgress(String jobId) {
        jobProgress.remove(jobId);
    }

    public void removeJobResult(String jobId) {
        jobResults.remove(jobId);
    }

    public Path getJobResult(String jobId) {
        return jobResults.get(jobId);
    }

    public ReplayRenderService(Path danserPath, Path songPath) {
        this.danserPath = danserPath;
        this.songPath = songPath;

        cleanUpExecutor.scheduleAtFixedRate(() -> {
            try {
                long fifteenMinutesAgo = System.currentTimeMillis() - (15 * 60 * 1000);

                Iterator<Map.Entry<String, Path>> iterator = jobResults.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<String, Path> entry = iterator.next();
                    String jobId = entry.getKey();
                    Path video = entry.getValue();

                    if (video != null && Files.getLastModifiedTime(video).toMillis() < fifteenMinutesAgo) {
                        Files.deleteIfExists(video);
                        jobProgress.remove(jobId);
                        iterator.remove();
                        LOG.info("Garbage Collector wiped stale job: {}", jobId);
                    }
                }
            } catch (Exception e) {
                LOG.error("Error during garbage collection of rendered videos", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }

    private void consumeDanserOutput(InputStream inputStream, String jobId) {
        Thread gobblerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = DANSER_PROGRESS_PATTERN.matcher(line);

                    if (matcher.find()) {
                        int progress = Integer.parseInt(matcher.group(1));
                        double speed = Double.parseDouble(matcher.group(2));
                        String eta = matcher.group(3);

                        jobProgress.put(jobId, new JobProgress(JobStatus.RENDERING, progress + "%", speed + "x", eta));
                    } else {
                        DANSER_LOG.info(line);
                    }
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
        jobProgress.put(jobId, new JobProgress(JobStatus.QUEUED));
        executor.submit(() -> render(osrPath, jobId));
        return jobId;
    }

    public String queueRenderShowcase(String beatmapId, List<Path> osrPaths) {
        final String jobId = UUID.randomUUID().toString();
        jobProgress.put(jobId, new JobProgress(JobStatus.QUEUED));
        executor.submit(() -> renderShowcase(beatmapId, osrPaths, jobId));
        return jobId;
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    private void render(Path osrPath, String jobId) {
        jobProgress.put(jobId, new JobProgress(JobStatus.RENDERING));
        Path tempSettingsFile = null;
        try {
            final List<String> c = new LinkedList<>();
            final String fileName = "replay_" + jobId;

            tempSettingsFile = prepareDanser(c);

            c.add("-replay=" + osrPath.toAbsolutePath());
            c.add("-out=" + fileName);

            runDanser(jobId, fileName, c);
        } catch (Exception e) {
            jobProgress.put(jobId, new JobProgress(JobStatus.FAILED));
            LOG.error("Danser failed to render video", e);
        } finally {
            if (tempSettingsFile != null) {
                try { Files.deleteIfExists(tempSettingsFile); } catch (IOException ignored) {}
            }
        }
    }

    private void renderShowcase(String beatmapId, List<Path> osrPaths, String jobId) {
        jobProgress.put(jobId, new JobProgress(JobStatus.RENDERING));
        Path tempSettingsFile = null;
        try {
            final List<String> c = new LinkedList<>();
            final String fileName = "showcase_" + jobId;

            tempSettingsFile = prepareDanser(c);

            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (Path osrPath : osrPaths) {
                String safePath = osrPath.toAbsolutePath().toString().replace("\\", "/");
                sb.append("\"").append(safePath).append("\"").append(",");
            }
            sb.deleteCharAt(sb.length() - 1).append("]");
            String replayList = sb.toString();

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                replayList = replayList.replace("\"", "\\\"");
            }

            c.add("-knockout2=" + replayList);
            c.add("-id=" + beatmapId);
            c.add("-out=" + fileName);

            runDanser(jobId, fileName, c);
        } catch (Exception e) {
            jobProgress.put(jobId, new JobProgress(JobStatus.FAILED));
            LOG.error("Danser failed to render showcase", e);
        } finally {
            if (tempSettingsFile != null) {
                try { Files.deleteIfExists(tempSettingsFile); } catch (IOException ignored) {}
            }
        }
    }

    private Path prepareDanser(List<String> c) throws IOException {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        if (!isWindows) {
            c.add("xvfb-run");
            c.add("-a");
        }

        c.add(danserPath.toAbsolutePath().toString());
        c.add("-noupdatecheck");
        c.add("-quickstart");
        c.add("-record");
        c.add("-preciseprogress");

        String safeSongPath = songPath.toAbsolutePath().toString().replace("\\", "/");

        try (InputStream templateStream = getClass().getResourceAsStream("/danser-config.json")) {
            if (templateStream == null) {
                throw new RuntimeException("Could not find danser-config.json in resources!");
            }

            String jsonTemplate = new String(templateStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String finalJsonContent = jsonTemplate.replace("{{OSU_SONGS_DIR}}", safeSongPath);

            Path settingsDir = danserPath.getParent().resolve("settings");
            Files.createDirectories(settingsDir);
            String tempProfileName = "ostella_temp_" + UUID.randomUUID().toString().substring(0, 8);
            Path tempSettingsFile = settingsDir.resolve(tempProfileName + ".json");
            Files.writeString(tempSettingsFile, finalJsonContent);

            c.add("-settings=" + tempProfileName);

            return tempSettingsFile;
        }
    }

    private void runDanser(String jobId, String fileName, List<String> c) throws IOException, InterruptedException {
        final Path videoPath = danserPath.resolve("..", "videos", fileName + ".mp4").normalize().toAbsolutePath();

        ProcessBuilder builder = new ProcessBuilder(c);
        builder.redirectErrorStream(true);
        LOG.info("Render started for {}", jobId);

        Process process = builder.start();

        consumeDanserOutput(process.getInputStream(), jobId);

        try {
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                jobProgress.put(jobId, new JobProgress(JobStatus.TIMEOUT));
                process.destroyForcibly();
                LOG.error("Danser timed out and was killed.");
                return;
            }
        } catch (InterruptedException e) {
            jobProgress.put(jobId,new JobProgress(JobStatus.FAILED));
            process.destroyForcibly();
            throw e;
        }

        if(!Files.exists(videoPath)) {
            jobProgress.put(jobId,new JobProgress(JobStatus.FAILED));
            LOG.error("Danser exited but no video rendered");
            throw new RuntimeException("Danser exited but no video rendered");
        }

        LOG.info("Danser finished rendering video: {}", fileName + ".mp4");
        jobProgress.put(jobId, new JobProgress(JobStatus.DONE));
        jobResults.put(jobId, videoPath);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        cleanUpExecutor.shutdownNow();
    }

    public record JobProgress(JobStatus status, String progress, String speed, String eta) {
        public JobProgress(JobStatus status) {
            this(status, null, null, null);
        }
    }

    public enum JobStatus {
        QUEUED,
        UNKNOWN,
        RENDERING,
        TIMEOUT,
        FAILED,
        DONE
    }
}
