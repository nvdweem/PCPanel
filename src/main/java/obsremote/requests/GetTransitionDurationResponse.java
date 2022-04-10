package obsremote.requests;

import com.google.gson.annotations.SerializedName;

public class GetTransitionDurationResponse extends ResponseBase {
    @SerializedName("transition-duration")
    private int transitionDuration;

    public int getTransitionDuration() {
        return transitionDuration;
    }
}

