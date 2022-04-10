package obsremote.requests;

import com.google.gson.annotations.SerializedName;

public class GetCurrentProfileResponse extends ResponseBase {
    @SerializedName("profile-name")
    private String profileName;

    public String getProfileName() {
        return profileName;
    }
}
