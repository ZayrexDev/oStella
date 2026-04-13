package xyz.zcraft.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.zcraft.model.MultiplayerRoom;
import xyz.zcraft.model.TokenData;
import xyz.zcraft.model.beatmap.BeatmapExtended;
import xyz.zcraft.model.beatmap.Beatmapset;
import xyz.zcraft.model.score.Score;
import xyz.zcraft.model.score.ScoreType;
import xyz.zcraft.model.user.UserExtended;
import xyz.zcraft.util.Config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedList;
import java.util.List;

public class OsuAPI {
    private static final HttpClient CLIENT = HttpClient.newBuilder().build();
    private static final String BASE_URL = "https://osu.ppy.sh/api/v2";
    private static final Gson GSON = new Gson();

    public static TokenData getToken(Config conf) {
        try {
            final JsonObject payload = new JsonObject();
            payload.addProperty("client_id", conf.clientId());
            payload.addProperty("client_secret", conf.clientSecret());
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
            throw new RuntimeException("Failed to get token!", e);
        }
    }

    public static List<Score> getUserScores(TokenData tokenData, String id, ScoreType mode, int limit, boolean includeFails) {
        try {
            final var request = newRequestBuilder(tokenData, String.format("/users/%s/scores/%s?mode=osu&limit=%d&include_fails=%d", id, mode.name().toLowerCase(), limit, includeFails ? 1 : 0))
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            final LinkedList<Score> scores = new LinkedList<>();
            JsonParser.parseString(body).getAsJsonArray().forEach(s -> {
                final Score e = GSON.fromJson(s, Score.class);
                scores.add(e);
            });

            return scores;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user!", e);
        }
    }

    public static Score getUserScore(TokenData tokenData, String u, String bm) {
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
            throw new RuntimeException("Failed to get user!", e);
        }
    }

    public static UserExtended getUser(TokenData tokenData, String id) {
        try {
            final var request = newRequestBuilder(tokenData, "/users/" + id)
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            return GSON.fromJson(body, UserExtended.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user!", e);
        }
    }

    public static List<MultiplayerRoom> getRooms(TokenData tokenData) {
        try {
            final var request = newRequestBuilder(tokenData, "/rooms")
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            final LinkedList<MultiplayerRoom> rooms = new LinkedList<>();
            JsonParser.parseString(body).getAsJsonArray().forEach(
                    s -> rooms.add(GSON.fromJson(s, MultiplayerRoom.class)));

            return rooms;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonObject byPassRequest(TokenData tokenData, String query) {
        try {
            final var request = newRequestBuilder(tokenData, query)
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            return JsonParser.parseString(body).getAsJsonObject();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Beatmapset getBeatmapset(TokenData tokenData, String setId) {
        try {
            final var request = newRequestBuilder(tokenData, "/beatmapsets/" + setId)
                    .GET()
                    .build();

            return GSON.fromJson(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body(), Beatmapset.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static BeatmapExtended getBeatmap(TokenData tokenData, String beatmapId) {
        try {
            final var request = newRequestBuilder(tokenData, "/beatmaps/" + beatmapId)
                    .GET()
                    .build();

            return GSON.fromJson(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body(), BeatmapExtended.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getBeatmapString(String beatmapId) {
        try {
            final var request = HttpRequest.newBuilder()
                    .uri(URI.create("https://osu.ppy.sh/osu/%s".formatted(beatmapId)))
                    .GET()
                    .build();

            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpRequest.Builder newRequestBuilder(TokenData tokenData, String endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + tokenData.token());
    }
}

