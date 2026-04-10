package xyz.zcraft.data;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

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

    public String status;

    public Boolean spotlight;

    public String title;

    @SerializedName("title_unicode")
    public String titleUnicode;

    @SerializedName("user_id")
    public Long userId;

    public Boolean video;

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

    @SerializedName("recent_favourites")
    public List<UserExtended> recentFavourites;

    @SerializedName("related_users")
    public List<UserExtended> relatedUsers;

    public UserExtended user;

    @SerializedName("track_id")
    public Long trackId;

    @Data
    public static class Covers {
        public String cover;

        @SerializedName("cover@2x")
        public String cover2x;

        public String card;

        @SerializedName("card@2x")
        public String card2x;

        public String list;

        @SerializedName("list@2x")
        public String list2x;

        public String slimcover;

        @SerializedName("slimcover@2x")
        public String slimcover2x;
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
