package xyz.zcraft.data;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class User {
    @SerializedName("avatar_url")
    private String avatarUrl;

    @SerializedName("country_code")
    private String countryCode;

    @SerializedName("default_group")
    private String defaultGroup;

    private long id;

    @SerializedName("is_active")
    private boolean isActive;

    @SerializedName("is_bot")
    private boolean isBot;

    @SerializedName("is_deleted")
    private boolean isDeleted;

    @SerializedName("is_online")
    private boolean isOnline;

    @SerializedName("is_supporter")
    private boolean isSupporter;

    @SerializedName("last_visit")
    private String lastVisit;

    @SerializedName("pm_friends_only")
    private boolean pmFriendsOnly;

    @SerializedName("profile_colour")
    private String profileColour;

    private String username;
}
