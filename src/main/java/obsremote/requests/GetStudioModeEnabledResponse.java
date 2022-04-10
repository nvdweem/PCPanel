package obsremote.requests;

import com.google.gson.annotations.SerializedName;

public class GetStudioModeEnabledResponse extends ResponseBase {
    @SerializedName("studio-mode")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }
}

