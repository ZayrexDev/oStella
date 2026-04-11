# oStella

`oStella` is a Java service that fetches osu! data and exposes simple HTTP endpoints for status, 
multiplayer info, and rendered score images.

It is the backend for [Seira](https://github.com/ZayrexDev/Seira) bot, 
and also provides a standalone API for other clients to consume.

## What You Get

- PNG score panels for best (`/bo`) and recent (`/rs`) osu! scores
- Multiplayer room summary endpoint (`/mp`)
- Current daily challenge endpoint (`/daily`)
- Health endpoint (`/status`)
- Automatic OAuth token renewal for osu! API

Here is a demo of my account:

<img width="1800" height="1278" alt="image" src="https://github.com/user-attachments/assets/ddad0426-7dbd-40cb-b38c-085afe569f6b" />

## Prerequisites

- JDK 25
- Maven 3.9+
- osu! OAuth app credentials (`client_id`, `client_secret`)

## Quick Start (5 Minutes)

1. Create `.env` in the project root.
2. Build the project.
3. Run the fat jar.
4. Call an endpoint.

### 1) Create `.env`

```env
OSU_CLIENT_ID=your_client_id
OSU_CLIENT_SECRET=your_client_secret
OSTELLA_PORT=8721
OSTELLA_MAX_THREADS=2
OSTELLA_DELAY=1000
OSTELLA_DEBUG=false
```

### 2) Build

```shell
mvn clean package
```

### 3) Run

```shell
java -jar target/oStella-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 4) Call an Endpoint

```shell
curl http://localhost:8721/bo?id=12345678&n=20 --output best_of_20.png
```

## Endpoints

Base URL: `http://localhost:<OSTELLA_PORT>`

| Endpoint  | Method | Query     | Response                              |
|-----------|--------|-----------|---------------------------------------|
| `/status` | GET    | none      | JSON health message                   |
| `/bo`     | GET    | `id`, `n` | `image/png` best-of-N panel           |
| `/rs`     | GET    | `id`, `n` | `image/png` recent-N panel            |
| `/mp`     | GET    | none      | JSON list of top multiplayer rooms    |
| `/daily`  | GET    | none      | JSON daily challenge room info or 404 |

## Configuration Reference

| Variable              | Required | Description                      |
|-----------------------|----------|----------------------------------|
| `OSU_CLIENT_ID`       | yes      | osu! OAuth client id             |
| `OSU_CLIENT_SECRET`   | yes      | osu! OAuth client secret         |
| `OSTELLA_PORT`        | yes      | HTTP server port                 |
| `OSTELLA_MAX_THREADS` | yes      | worker pool size for async tasks |
| `OSTELLA_DELAY`       | yes      | delay before requests            |

Notes:

- `/bo` and `/rs` require numeric `n`; invalid params return HTTP `400`.
- Score endpoints currently call osu! API with `mode=osu`.

## Build Artifacts

`mvn clean package` generates:

- `target/oStella-1.0-SNAPSHOT.jar`
- `target/oStella-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Playwright Note

Image rendering depends on Playwright Chromium. If your environment is missing browser binaries, 
when first started, Playwright will attempt to download them.

## Logs

Log files are written to `logs/`:

- `latest.log` (application logs)
- `javalin-server.log` (Javalin/Jetty logs)
- rolled `*.log.gz` archives

## Key Source Files

- `src/main/java/xyz/zcraft/oStella.java` - app bootstrap
- `src/main/java/xyz/zcraft/network/WebServer.java` - routes/handlers
- `src/main/java/xyz/zcraft/network/NetworkHelper.java` - osu! API requests
- `src/main/java/xyz/zcraft/util/TokenManager.java` - OAuth token lifecycle
- `src/main/java/xyz/zcraft/util/ScoreRenderer.java` - HTML to PNG rendering
- `src/main/resources/templates/stat.html` - score panel template

## Troubleshooting

- `Invalid configuration` at startup:
  - Check `.env` values are present and numeric fields are valid (`OSTELLA_PORT`, `OSTELLA_MAX_THREADS`, `OSTELLA_DELAY`).
- Playwright launch/render issues:
  - Install Chromium with the command in "Playwright Note" and restart.
- Endpoint timeout/slowness:
  - Increase `OSTELLA_MAX_THREADS`, lower `OSTELLA_DELAY`, and verify upstream osu! API health.

## License

MIT. See `LICENSE`.
