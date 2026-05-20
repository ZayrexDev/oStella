package xyz.zcraft.ostella.network;

import com.google.gson.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.zcraft.ostella.config.AppConfig;
import xyz.zcraft.ostella.data.ScoreType;
import xyz.zcraft.ostella.data.TokenData;
import xyz.zcraft.ostella.service.CacheService;
import xyz.zcraft.osu.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class OsuAPI {
    private static final Logger LOG = LogManager.getLogger(OsuAPI.class);
    private static final HttpClient CLIENT = HttpClient.newBuilder().build();
    private static final String BASE_URL = "https://osu.ppy.sh/api/v2";
    private static final Gson GSON = new Gson();

    public static TokenData getToken(AppConfig conf) {
        try {
            final JsonObject payload = new JsonObject();
            payload.addProperty("client_id", conf.osu().clientId());
            payload.addProperty("client_secret", conf.osu().clientSecret());
            payload.addProperty("grant_type", "client_credentials");
            payload.addProperty("scope", "public");

            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://osu.ppy.sh/oauth/token"))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            final JsonObject asJsonObject = JsonParser.parseString(body).getAsJsonObject();
            return new TokenData(
                    asJsonObject.get("access_token").getAsString(),
                    System.currentTimeMillis(),
                    asJsonObject.get("expires_in").getAsLong()
            );
        } catch (Exception e) {
            throw new ApiException(ErrorCode.TOKEN_FETCH_FAILED, "Failed to fetch token", e);
        }
    }

    public static Score getScore(TokenData tokenData, String scoreId) {
        LOG.debug("Fetching score with id {}", scoreId);

        try {
            final Optional<Score> scoreJsonCache = CacheService.getScoreJsonCache(scoreId);

            if (scoreJsonCache.isPresent()) {
                LOG.debug("Score {} found in cache", scoreId);
                return scoreJsonCache.get();
            }
        } catch (Exception e) {
            LOG.warn("Failed to get score from cache for score id {}: {}", scoreId, e.getMessage());
        }

        try {
            final var request = newRequestBuilder(tokenData, "/scores/" + scoreId)
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            if (JsonParser.parseString(body).getAsJsonObject().has("error")) {
                return null;
            }

            final Score score = GSON.fromJson(body, Score.class);

            try {
                CacheService.cacheScoreJson(score);
                LOG.debug("Score {} cached", scoreId);
            } catch (IOException e) {
                LOG.warn("Failed to cache score for score id {}", scoreId, e);
            }

            return score;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.SCORE_FETCH_FAILED, "Failed to get score id " + scoreId, e);
        }
    }

    public static List<Score> getUserScores(TokenData tokenData, String id, ScoreType mode, int limit, boolean includeFails) {
        LOG.debug("Fetching {} scores for user id {} in mode {}", mode.name().toLowerCase(), id, mode.name().toLowerCase());
        try {
            final var request = newRequestBuilder(tokenData, String.format("/users/%s/scores/%s?mode=osu&limit=%d&include_fails=%d", id, mode.name().toLowerCase(), limit, includeFails ? 1 : 0))
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            final LinkedList<Score> scores = new LinkedList<>();
            JsonParser.parseString(body).getAsJsonArray().forEach(s -> {
                final Score e = GSON.fromJson(s, Score.class);
                e.getBeatmap().setBeatmapset(e.getBeatmapset());
                scores.add(e);
            });

            return scores;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.SCORE_FETCH_FAILED, "Failed to fetch scores for user id " + id, e);
        }
    }

    public static Score getUserScore(TokenData tokenData, String u, String bm) {
        LOG.debug("Fetching score for user id {} on beatmap id {}", u, bm);
        try {
            final var request = newRequestBuilder(tokenData, String.format("/beatmaps/%s/scores/users/%s", bm, u))
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            if (JsonParser.parseString(body).getAsJsonObject().has("error")) {
                return null;
            }

            return GSON.fromJson(JsonParser.parseString(body).getAsJsonObject().get("score").getAsJsonObject(), Score.class);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.SCORE_FETCH_FAILED, "Failed to fetch scores for " + u + " on beatmap " + bm, e);
        }
    }

    public static MultiplayerRoom getCurrentRoom(String auth) {
        LOG.debug("Getting current room");
        try {
            final var request = newRequestBuilder(auth, "/rooms?mode=participated&type_group=realtime&is_active=true")
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new ApiException(
                        ErrorCode.UNAUTHORIZED,
                        "Unauthorized to fetch current room: status " + response.statusCode()
                );
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.ROOM_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " while fetching current room"
                );
            }

            final JsonElement jsonElement = JsonParser.parseString(response.body());
            if (!jsonElement.isJsonArray()) {
                if (jsonElement.isJsonObject() && jsonElement.getAsJsonObject().has("error")) {
                    return null;
                }
                throw new ApiException(ErrorCode.ROOM_FETCH_FAILED, "Unexpected response while fetching current room");
            }

            final JsonArray arr = jsonElement.getAsJsonArray();

            if (arr.isEmpty()) {
                return null;
            }

            return GSON.fromJson(arr.get(0), MultiplayerRoom.class);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.ROOM_FETCH_FAILED, "Failed to fetch current room", e);
        }
    }

    public static UserExtended getUser(TokenData tokenData, String id) {
        LOG.debug("Fetching user with id {}", id);
        try {
            final var request = newRequestBuilder(tokenData, "/users/" + id)
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.USER_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for user " + id
                );
            }

            return GSON.fromJson(response.body(), UserExtended.class);
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.USER_FETCH_FAILED, "Network failed to get user id " + id, e);
        }
    }

    public static List<User> getUsers(TokenData tokenData, List<String> ids) {
        LOG.debug("Fetching users with ids {}", () -> Arrays.toString(ids.toArray()));
        StringBuilder sb = new StringBuilder("?");
        for (String id : ids) {
            sb.append("ids[]=").append(id).append("&");
        }
        try {
            final var request = newRequestBuilder(tokenData, "/users" + sb)
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.USER_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for user " + Arrays.toString(ids.toArray())
                );
            }

            final String body = response.body();

            final JsonArray users = JsonParser.parseString(body).getAsJsonObject().get("users").getAsJsonArray();
            final LinkedList<User> userList = new LinkedList<>();
            users.forEach(u -> userList.add(GSON.fromJson(u, User.class)));
            return userList;
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.USER_FETCH_FAILED, "Failed to get users with ids " + ids, e);
        }
    }

    public static List<Beatmapset> searchBeatmapset(TokenData tokenData, String queryString) {
        LOG.debug("Fetching beatmapsets for query {}", queryString);
        try {
            final var request = newRequestBuilder(tokenData, "/beatmapsets/search?m=0&q=" + URLEncoder.encode(queryString, StandardCharsets.UTF_8))
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.BEATMAP_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " query " + queryString
                );
            }

            final String body = response.body();

            final LinkedList<Beatmapset> beatmapsets = new LinkedList<>();

            JsonParser.parseString(body).getAsJsonObject().get("beatmapsets").getAsJsonArray()
                    .forEach(e -> beatmapsets.add(GSON.fromJson(e, Beatmapset.class)));

            return beatmapsets;
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.BEATMAPSET_FETCH_FAILED, "Failed to search beatmapsets with query " + queryString, e);
        }
    }

    public static List<MultiplayerRoom> getRooms(TokenData tokenData) {
        LOG.debug("Fetching multiplayer rooms");
        try {
            final var request = newRequestBuilder(tokenData, "/rooms")
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.ROOM_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " getting rooms"
                );
            }

            final String body = response.body();

            final LinkedList<MultiplayerRoom> rooms = new LinkedList<>();
            JsonParser.parseString(body).getAsJsonArray().forEach(
                    s -> rooms.add(GSON.fromJson(s, MultiplayerRoom.class)));

            return rooms;
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.ROOM_FETCH_FAILED, "Failed to fetch rooms", e);
        }
    }

    public static JsonObject byPassRequest(TokenData tokenData, String query) {
        LOG.debug("Making bypass request with query {}", query);
        try {
            final var request = newRequestBuilder(tokenData, query)
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.FETCH_FAILED,
                        "osu! API returned status " + response.statusCode()
                );
            }

            final String body = response.body();

            return JsonParser.parseString(body).getAsJsonObject();
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.ILLEGAL_ARGUMENT, "Failed to make bypass request with query " + query, e);
        }
    }

    public static Beatmapset getBeatmapset(TokenData tokenData, String setId) {
        LOG.debug("Fetching beatmapset with id {}", setId);
        try {
            final var request = newRequestBuilder(tokenData, "/beatmapsets/" + setId)
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.BEATMAPSET_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for beatmapset " + setId
                );
            }

            final String body = response.body();
            if (JsonParser.parseString(body).getAsJsonObject().has("error")) {
                return null;
            }
            return GSON.fromJson(body, Beatmapset.class);
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.BEATMAPSET_FETCH_FAILED, "Failed to get beatmapset id " + setId, e);
        }
    }

    public static Beatmapset getBeatmapsetFromBeatmap(TokenData tokenData, String beatmapId) {
        LOG.debug("Fetching beatmapset with id {}", beatmapId);
        try {
            final var request = newRequestBuilder(tokenData, "/beatmapsets/lookup?beatmap_id=" + beatmapId)
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.BEATMAPSET_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for beatmap " + beatmapId
                );
            }

            final String body = response.body();
            if (JsonParser.parseString(body).getAsJsonObject().has("error")) {
                return null;
            }
            return GSON.fromJson(body, Beatmapset.class);
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.BEATMAPSET_FETCH_FAILED, "Failed to get beatmapset for beatmap id " + beatmapId, e);
        }
    }

    public static BeatmapExtended getBeatmap(TokenData tokenData, String beatmapId) {
        LOG.debug("Fetching beatmap with id {}", beatmapId);
        try {
            final var request = newRequestBuilder(tokenData, "/beatmaps/" + beatmapId)
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.BEATMAP_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for beatmap " + beatmapId
                );
            }

            final String body = response.body();
            if (JsonParser.parseString(body).getAsJsonObject().has("error")) {
                return null;
            }
            return GSON.fromJson(body, BeatmapExtended.class);
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.BEATMAP_FETCH_FAILED, "Failed to get beatmap id " + beatmapId, e);
        }
    }

    public static byte[] getBeatmapBytes(String beatmapId) {
        LOG.debug("Fetching beatmap bytes with id {}", beatmapId);
        try {
            final var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://osu.ppy.sh/osu/%s".formatted(beatmapId)))
                    .GET()
                    .build();

            final HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.BEATMAP_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for beatmap " + beatmapId
                );
            }

            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.BEATMAP_FETCH_FAILED, "Failed to get beatmap bytes for beatmap id " + beatmapId, e);
        }
    }

    private static HttpRequest.Builder newRequestBuilder(TokenData tokenData, String endpoint) {
        return newRequestBuilder("Bearer " + tokenData.token(), endpoint);
    }

    private static HttpRequest.Builder newRequestBuilder(String auth, String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", auth);
    }

    public static byte[] getImageBytes(String url) {
        try {
            final var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            final HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for img " + url
                );
            }

            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.IMAGE_FETCH_FAILED, "Failed to get image bytes for url " + url, e);
        }
    }

    public static byte[] getReplayBytes(TokenData tokenData, String id) {
        try {
            final var request = newRequestBuilder(tokenData, "/scores/" + id + "/download")
                    .GET()
                    .build();

            final HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.REPLAY_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for replay " + id
                );
            }

            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.REPLAY_FETCH_FAILED, "Failed to get replay bytes for score id " + id, e);
        }
    }

    public static boolean isOsuApiHealthy(TokenData tokenData) {
        try {
            HttpRequest request = newRequestBuilder(tokenData, "/users/2/osu")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<UserRelation> getFriends(String auth) {
        LOG.debug("Fetching friends");
        try {
            final var request = newRequestBuilder(auth, "/friends")
                    .header("X-Api-Version", "20241022")
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.USER_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for friend list"
                );
            }

            final JsonArray users = JsonParser.parseString(response.body()).getAsJsonArray();
            final LinkedList<UserRelation> userList = new LinkedList<>();
            users.forEach(u -> userList.add(GSON.fromJson(u, UserRelation.class)));
            return userList;
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.USER_FETCH_FAILED, "Network failed to get friend list", e);
        }
    }

    public static User getSelf(String auth) {
        LOG.debug("Fetching self");
        try {
            final var request = newRequestBuilder(auth, "/me")
                    .GET()
                    .build();

            final HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                return null;
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(
                        ErrorCode.USER_FETCH_FAILED,
                        "osu! API returned status " + response.statusCode() + " for friend list"
                );
            }

            final var json = JsonParser.parseString(response.body()).getAsJsonObject();
            return GSON.fromJson(json, User.class);
        } catch (IOException | InterruptedException e) {
            throw new ApiException(ErrorCode.USER_FETCH_FAILED, "Network failed to get friend list", e);
        }
    }
}

