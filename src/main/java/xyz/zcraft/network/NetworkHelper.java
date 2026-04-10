package xyz.zcraft.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.zcraft.data.*;
import xyz.zcraft.util.Config;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedList;
import java.util.List;

public class NetworkHelper {
    private static final HttpClient CLIENT = HttpClient.newBuilder().build();

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
            final var request = newRequestBuilder(tokenData)
                    .uri(URI.create(String.format("https://osu.ppy.sh/api/v2/users/%s/scores/%s?mode=osu&limit=%d&include_fails=%d", id, mode.name().toLowerCase(), limit, includeFails ? 1 : 0)))
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            final LinkedList<Score> scores = new LinkedList<>();
            JsonParser.parseString(body).getAsJsonArray().forEach(s -> {
                final Score e = new Gson().fromJson(s, Score.class);
                scores.add(e);
            });

            return scores;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user!", e);
        }
    }

    public static UserExtended getUser(String id, TokenData tokenData) {
        try {
            final var request = newRequestBuilder(tokenData)
                    .uri(URI.create(String.format("https://osu.ppy.sh/api/v2/users/%s", id)))
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            return new Gson().fromJson(body, UserExtended.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user!", e);
        }
    }

    public static List<MultiplayerRoom> getRooms(TokenData tokenData) {
        try {
            final var request = newRequestBuilder(tokenData)
                    .uri(URI.create("https://osu.ppy.sh/api/v2/rooms"))
                    .GET()
                    .build();

            final String body = CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();

            final LinkedList<MultiplayerRoom> rooms = new LinkedList<>();
            JsonParser.parseString(body).getAsJsonArray().forEach(
                    s -> rooms.add(new Gson().fromJson(s, MultiplayerRoom.class)));

            return rooms;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpRequest.Builder newRequestBuilder(TokenData tokenData) {
        return HttpRequest.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + tokenData.token());
    }
}

