package xyz.zcraft.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class MultiplayerRoom {
    private long id;
    private String name;
    private String description;
    private String category;
    private String status;
    private String type;

    @SerializedName("user_id")
    private long userId;

    @SerializedName("starts_at")
    private String startsAt;

    @SerializedName("ends_at")
    private String endsAt;

    @SerializedName("max_attempts")
    private Integer maxAttempts;

    @SerializedName("participant_count")
    private int participantCount;

    @SerializedName("channel_id")
    private long channelId;

    private boolean active;

    @SerializedName("has_password")
    private boolean hasPassword;

    @SerializedName("queue_mode")
    private String queueMode;

    @SerializedName("auto_skip")
    private boolean autoSkip;

    private boolean pinned;

    @SerializedName("current_playlist_item")
    private CurrentPlaylistItem currentPlaylistItem;

    @SerializedName("difficulty_range")
    private DifficultyRange difficultyRange;

    private User host;

    @SerializedName("playlist_item_stats")
    private PlaylistItemStats playlistItemStats;

    @SerializedName("recent_participants")
    private List<User> recentParticipants;

    @Data
    public static class CurrentPlaylistItem {
        private long id;

        @SerializedName("room_id")
        private long roomId;

        @SerializedName("beatmap_id")
        private long beatmapId;

        @SerializedName("created_at")
        private String createdAt;

        @SerializedName("ruleset_id")
        private int rulesetId;

        @SerializedName("allowed_mods")
        private List<Mod> allowedMods;

        @SerializedName("required_mods")
        private List<Mod> requiredMods;

        private boolean freestyle;
        private boolean expired;

        @SerializedName("owner_id")
        private long ownerId;

        @SerializedName("playlist_order")
        private Integer playlistOrder;

        @SerializedName("played_at")
        private String playedAt;

        private BeatmapExtended beatmap;
    }

    @Data
    public static class PlaylistItemStats {
        @SerializedName("count_active")
        private int countActive;

        @SerializedName("count_total")
        private int countTotal;

        @SerializedName("ruleset_ids")
        private List<Integer> rulesetIds;
    }

}
