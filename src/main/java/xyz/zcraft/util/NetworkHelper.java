package xyz.zcraft.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.zcraft.Config;
import xyz.zcraft.data.Score;
import xyz.zcraft.data.TokenData;
import xyz.zcraft.data.UserExtended;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedList;
import java.util.List;

public class NetworkHelper {
    public static TokenData getToken(Config conf) {
        try (final HttpClient client = newClient()) {
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

            final String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

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

    public static List<Score> getUserScores(String id, TokenData tokenData, int limit) {
        try (final HttpClient client = newClient()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("https://osu.ppy.sh/api/v2/users/%s/scores/best?mode=osu&limit=%d", id, limit)))
                    .GET()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + tokenData.token())
                    .build();

            final String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

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
        try (final HttpClient client = newClient()) {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format("https://osu.ppy.sh/api/v2/users/%s", id)))
                    .GET()
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + tokenData.token())
                    .build();

            final String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();

            return new Gson().fromJson(body, UserExtended.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get user!", e);
        }
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder().build();
    }
}

