package xyz.zcraft.ostella.model.user;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserExtended extends User {
    @SerializedName("cover_url")
    public String coverUrl;

    public String discord;

    @SerializedName("has_supported")
    public Boolean hasSupported;

    public String interests;

    @SerializedName("join_date")
    public String joinDate;

    public Kudosu kudosu;

    public String location;

    @SerializedName("max_blocks")
    public Integer maxBlocks;

    @SerializedName("max_friends")
    public Integer maxFriends;

    public String occupation;

    public String playmode;

    public List<String> playstyle;

    @SerializedName("post_count")
    public Integer postCount;

    @SerializedName("profile_hue")
    public Integer profileHue;

    @SerializedName("profile_order")
    public List<String> profileOrder;

    public String title;

    public String twitter;

    public String website;

    public Country country;

    public Cover cover;

    @SerializedName("is_restricted")
    public Boolean isRestricted;

    @SerializedName("account_history")
    public List<JsonObject> accountHistory;

    public List<Badge> badges;

    @SerializedName("favourite_beatmapset_count")
    public Long favouriteBeatmapsetCount;

    @SerializedName("follower_count")
    public Long followerCount;

    @SerializedName("graveyard_beatmapset_count")
    public Long graveyardBeatmapsetCount;

    public List<Group> groups;

    @SerializedName("loved_beatmapset_count")
    public Long lovedBeatmapsetCount;

    @SerializedName("monthly_playcounts")
    public List<MonthlyPlaycount> monthlyPlaycounts;

    public Page page;

    @SerializedName("pending_beatmapset_count")
    public Long pendingBeatmapsetCount;

    @SerializedName("previous_usernames")
    public List<String> previousUsernames;

    @SerializedName("ranked_beatmapset_count")
    public Long rankedBeatmapsetCount;

    @SerializedName("replays_watched_counts")
    public List<MonthlyPlaycount> replaysWatchedCounts;

    @SerializedName("scores_first_count")
    public Long scoresFirstCount;

    public Statistics statistics;

    @SerializedName("support_level")
    public Integer supportLevel;

    @SerializedName("user_achievements")
    public List<UserAchievement> userAchievements;

    @SerializedName("rank_history")
    public RankHistory rankHistory;
    public Team team;


    @Data
    public static class Kudosu {
        public Integer total;
        public Integer available;
    }

    @Data
    public static class Country {
        public String code;
        public String name;
    }

    @Data
    public static class Cover {
        @SerializedName("custom_url")
        public String customUrl;

        public String url;

        public Long id;
    }

    @Data
    public static class Badge {
        @SerializedName("awarded_at")
        public String awardedAt;

        public String description;

        @SerializedName("image@2x_url")
        public String image2xUrl;

        @SerializedName("image_url")
        public String imageUrl;

        public String url;
    }

    @Data
    public static class Group {
        public Long id;
        public String identifier;
        public String name;

        @SerializedName("short_name")
        public String shortName;

        public String description;
        public String colour;
    }

    @Data
    public static class MonthlyPlaycount {
        @SerializedName("start_date")
        public String startDate;

        public Long count;
    }

    @Data
    public static class Page {
        public String html;
        public String raw;
    }

    @Data
    public static class Team {
        public Integer id;
        public String name;
        @SerializedName("short_name")
        public String shortName;
        @SerializedName("flag_url")
        public String flagUrl;
    }

    @Data
    public static class Level {
        public Integer current;
        public Integer progress;
    }

    @Data
    public static class GradeCounts {
        public Long ss;
        public Long ssh;
        public Long s;
        public Long sh;
        public Long a;
    }

    @Data
    public static class Rank {
        public Long global;
        public Long country;
    }

    @Data
    public static class UserAchievement {
        @SerializedName("achieved_at")
        public String achievedAt;

        @SerializedName("achievement_id")
        public Long achievementId;
    }

    @Data
    public static class RankHistory {
        public String mode;
        public List<Long> data;
    }
}
