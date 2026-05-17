# oStella

`oStella` is a Java service that fetches osu! data and exposes simple HTTP endpoints for status,
multiplayer info, and rendered osu! images.

It is the backend for [Seira](https://github.com/ZayrexDev/Seira) bot, 
and also provides a standalone API for other clients to consume.

## What You Get

- PNG score panels for best (`/bestof`) and recent (`/recent`) osu! scores
- PNG beatmap card endpoint (`/beatmap`)
- PNG beatmapset card endpoint (`/beatmapset`)
- PNG player comparison leaderboard endpoint (`/mapleadboard`)
- PNG user PP leaderboard endpoint (`/leaderboard`)
- Replay video generation for solo and multiplayer replay showcases (`/replay`)
- Multiplayer room summary endpoint (`/mp`)
- Current daily challenge endpoint (`/daily`)
- Health endpoint (`/status`)
- Automatic OAuth token renewal for osu! API

Here are some demo:

### Best-of-N
<img width="800" alt="image" src="https://github.com/user-attachments/assets/a10742b8-148d-4fab-90b3-3ecd6882e2ae" />

### Beatmap Card
<img width="400" alt="image" src="https://github.com/user-attachments/assets/41f36596-94c7-4407-af82-490efccf6704" />

### Group Leaderboard
<img width="400" alt="image" src="https://github.com/user-attachments/assets/cbc361a4-61e0-440d-908e-f1d52009373e" />

### Score Card
<img width="400" alt="image" src="https://github.com/user-attachments/assets/c3ac7fde-adea-427b-be0a-9871a45ca8ad" />

### Beatmapset Card
<img width="400" alt="image" src="https://github.com/user-attachments/assets/2bb887d6-5fff-429b-b39e-963235ec463e" />

### Replay Video

<img width="400" alt="image" src="https://github.com/user-attachments/assets/1a2b993d-f9c5-481f-97eb-30613d3192f7" />
<img width="400" alt="image" src="https://github.com/user-attachments/assets/45946964-7dd6-4f8a-a346-a83be0b00803" />

## Prerequisites

- JDK 25
- Maven 3.9+
- osu! OAuth app credentials (`client_id`, `client_secret`)

## Quick Start (5 Minutes)

1. Copy default config file in the project root.
2. Install Playwright dependencies if not already present.
3. Run.
4. Call an endpoint.

### 1) Create `config.yml`

The default config file is generated as `config.yml` when you first start the service. 
You can also copy the example config from [ostella-example-config.yml](/src/main/resources/ostella-example-config.yml)

### 2) Install Playwright Dependencies

```shell
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps"
```

### 3) Run

```shell
mvn -U clean compile exec:java
```

### 4) Call an Endpoint

```shell
curl "http://localhost:8721/bo?u=12345678&n=20" --output best_of_20.png
```

## Endpoints

Base URL: `http://localhost:<OSTELLA_PORT>`

Most JSON endpoints return: `{"success": boolean, "message": string, "data": any}`.
Image endpoints return PNG bytes. Replay download returns `video/mp4`.

### Core Endpoints

| Method | Path           | Purpose                              | Query / Path Params            | Response |
|--------|----------------|--------------------------------------|--------------------------------|----------|
| GET    | `/status`      | Service health and osu! API health   | none                           | JSON     |
| GET    | `/daily`       | Current daily challenge room summary | none                           | JSON     |
| GET    | `/mp`          | Top multiplayer rooms (up to 20)     | none                           | JSON     |
| GET    | `/searchms`    | Search beatmapsets                   | `q` (search keyword)           | JSON     |
| GET    | `/leaderboard` | User PP leaderboard image            | `u` (comma-separated user IDs) | PNG      |
| GET    | `/bestof`      | Best-of-N scores image               | `u` (user ID), `n` (count)     | PNG      |
| GET    | `/recent`      | Recent scores image                  | `u` (user ID), `n` (count)     | PNG      |

### Beatmap / Beatmapset / Score / PK

| Method | Path          | Purpose                        | Query / Path Params                                                                 | Response |
|--------|---------------|--------------------------------|-------------------------------------------------------------------------------------|----------|
| GET    | `/beatmap`    | Beatmap card image             | `m` (+ optional `mod`) **or** `ms` + `i` (+ optional `mod`) **or** `of` + `u` + `i` | PNG      |
| GET    | `/beatmapset` | Beatmapset card image          | `ms` **or** `m` **or** `of` + `u` + `i`                                             | PNG      |
| GET    | `/score`      | Score card image               | `s` **or** `m` + `u` **or** `ms` + `i` + `u` **or** `of` + `u` + `i`                | PNG      |
| GET    | `/maplb`      | Compare players on one beatmap | `m` + `u` (comma-separated user IDs) **or** `of` + `i` + `us` + `u`                 | PNG      |

Notes:
- `of` references source list type (`rs` or `bo`).
- `i` is a 1-based index within the referenced list or sorted beatmapset difficulties.

### Replay Endpoints (enabled only when `danserPath` is configured)

| Method | Path                     | Purpose                                | Query / Path Params                                                             | Response    |
|--------|--------------------------|----------------------------------------|---------------------------------------------------------------------------------|-------------|
| GET    | `/replay/status`         | Replay renderer overview               | none                                                                            | JSON        |
| GET    | `/replay/render`         | Queue single replay render             | `s` **or** `m` + `u` **or** `ms` + `i` + `u` **or** `of` + `u` + `i`            | `202` JSON  |
| GET    | `/replay/showcase`       | Queue multi-score showcase render      | `s` (comma-separated score IDs) **or** `u` + `m` **or** `of` + `i` + `us` + `u` | `202` JSON  |
| GET    | `/replay/status/{jobId}` | Get render job state                   | `{jobId}`                                                                       | JSON        |
| GET    | `/replay/video/{jobId}`  | Download rendered video                | `{jobId}`                                                                       | `video/mp4` |
| DELETE | `/replay/video/{jobId}`  | Remove rendered video and job metadata | `{jobId}`                                                                       | text        |

### Debug Endpoint (debug mode only)

| Method | Path            | Purpose                                   | Query / Path Params | Response |
|--------|-----------------|-------------------------------------------|---------------------|----------|
| GET    | `/debug/bypass` | Debug passthrough call to osu! API helper | raw query string    | JSON     |

## Cache Behavior

`oStella` caches downloaded assets on disk to reduce repeated upstream requests.

- Beatmap files used for difficulty/PP calculation are cached in `data/cache/beatmap/`.
- Remote images used by templates (avatars, covers, flags) are cached in `data/cache/image/`.
- Image cache keys are generated from the image URL, so the same URL reuses the same cached file.
- Cache is file-based and persists across restarts.
- Public HTTP endpoints currently use on-demand caching (download when missing).

To clear cache, stop the service and remove files under `data/cache/`; they will be re-downloaded on future requests.

## Build Artifacts

`mvn clean package` generates:

- `target/oStella-{version}.jar`
- `target/oStella-{version}-jar-with-dependencies.jar`

## Playwright Note

Image rendering depends on Playwright Chromium. If your environment is missing browser binaries, 
when first started, Playwright will attempt to download them.

## Performance & Requirements

oStella is designed to be highly concurrent, but its resource usage scales directly
with how you configure its rendering features. The core web server is incredibly lightweight,
but image (Playwright/Chromium) and video (Danser) rendering require careful hardware consideration.

### Minimum System Requirements
* **CPU:** 2+ Cores (4+ Cores heavily recommended if video rendering is enabled)
* **RAM:** 2 GB minimum (4 GB recommended for stable multi-worker rendering)
* **Storage:** 5+ GB free space (for caching osu! beatmaps, replays, and rendered videos)

### RAM Usage
> Memory consumption is strictly controlled by your worker pool configurations.
By default, oStella prevents Out-Of-Memory crashes by queuing requests rather
than spawning infinite browser instances.

* **Core Java Server:** ~250MB - 500MB (depending on JVM garbage collection and cache size).
* **Image Rendering (Playwright/Chromium):** ~100MB - 150MB per active worker.
  If you configure `ostella.renderWorkers: 4`, expect Chromium to reserve up to ~600MB of RAM under peak load.
* **Video Rendering (Danser):** ~200MB - 300MB per active Danser instance during an active render.

### CPU Usage
* **API Routing & Network:** Near 0% CPU impact. Asynchronous request handling allows the server to idle efficiently.
* **Image Rendering:** Moderate, bursty CPU usage. Chromium utilizes separate OS processes for rendering,
  meaning concurrent image requests will actively utilize multiple CPU cores for brief moments.
* **Video Rendering (Replays):** **Extreme CPU usage.** Software encoding (e.g., `libx264`) will easily pin your CPU to 100%.

### Low Resource Environments?
If you are running oStella on a low-resource environment (e.g., 2GB RAM, 2 CPU cores), it is crucial to:
1. Limit your Playwright worker pool to `2` or `3` to prevent memory exhaustion.
2. Avoid enabling video rendering or limit it to a single worker with hardware encoding.
3. Monitor your server's resource usage closely, especially under load, to ensure it remains responsive

## Logs

Log files are written to `logs/`:

- `latest.log` (application logs)
- `javalin-server.log` (Javalin/Jetty logs)
- `danser.log` (Danser-CLI logs)
- rolled `*.log.gz` archives

## License

MIT. See `LICENSE`.
