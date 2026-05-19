package xyz.zcraft.ostella.model.beatmap;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import xyz.zcraft.ostella.model.user.UserExtended;

import java.util.List;
import java.util.Optional;

@Data
public class Beatmapset {
    public String artist;

    @SerializedName("artist_unicode")
    public String artistUnicode;

    public Covers covers;

    public String creator;

    @SerializedName("favourite_count")
    public Long favouriteCount;

    public Long id;

    public Boolean nsfw;

    public Integer offset;

    @SerializedName("play_count")
    public Long playCount;

    @SerializedName("preview_url")
    public String previewUrl;

    public String source;

    @SerializedName("genre_id")
    public Integer genreId;

    @SerializedName("language_id")
    public Integer languageId;

    public String status;
    public String tags;

    public Boolean spotlight;

    public String title;

    @SerializedName("title_unicode")
    public String titleUnicode;

    @SerializedName("user_id")
    public Long userId;

    public Boolean video = false;
    public Boolean storyboard = false;

    public List<BeatmapExtended> beatmaps;

    public List<BeatmapExtended> converts;

    @SerializedName("current_nominations")
    public List<JsonObject> currentNominations;

    @SerializedName("current_user_attributes")
    public JsonObject currentUserAttributes;

    public Description description;

    public List<JsonObject> discussions;

    public List<JsonObject> events;

    public Label genre;

    @SerializedName("has_favourited")
    public Boolean hasFavourited;

    public Label language;

    public List<JsonObject> nominations;

    @SerializedName("pack_tags")
    public List<String> packTags;

    public List<Integer> ratings;
    public Double rating;
    public Double bpm;
    @SerializedName("related_tags")
    public List<UserTag> relatedTags;
    @SerializedName("recent_favourites")
    public List<UserExtended> recentFavourites;
    @SerializedName("related_users")
    public List<UserExtended> relatedUsers;
    public UserExtended user;
    @SerializedName("track_id")
    public Long trackId;

    public String getTagName(int id) {
        if (relatedTags == null) return null;

        return relatedTags.stream().filter(tag -> tag.getId() == id)
                .findFirst()
                .map(UserTag::getName)
                .orElse(null);
    }

    public String getRatingString() {
        return Optional.ofNullable(rating)
                .map(r -> String.format("%.2f", r))
                .orElse("--");
    }

    public String getGenreString() {
        return Optional.ofNullable(genre)
                .map(g -> g.name)
                .filter(s -> !s.isBlank())

                .orElse("--");
    }

    public String getSourceString() {
        return Optional.ofNullable(source)
                .filter(s -> !s.isBlank())
                .orElse("--");
    }

    public String getLanguageString() {
        return Optional.ofNullable(language)
                .map(k -> k.name)
                .filter(s -> !s.isBlank())
                .orElse("--");
    }

    public double getMaxStar() {
        if (beatmaps == null || beatmaps.isEmpty()) return 0;
        double result = beatmaps.getFirst().getDifficultyRating();
        for (BeatmapExtended beatmap : beatmaps) {
            result = Math.max(beatmap.getDifficultyRating(), result);
        }
        return result;
    }

    public double getMinStar() {
        if (beatmaps == null || beatmaps.isEmpty()) return 0;
        double result = beatmaps.getFirst().getDifficultyRating();
        for (BeatmapExtended beatmap : beatmaps) {
            result = Math.min(beatmap.getDifficultyRating(), result);
        }
        return result;
    }

    @Data
    public static class Description {
        public String description;
        public String bbcode;
    }

    @Data
    public static class Label {
        public Integer id;
        public String name;
    }
}
