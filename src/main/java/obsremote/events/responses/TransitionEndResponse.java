package obsremote.events.responses;

import com.google.gson.annotations.SerializedName;

import obsremote.requests.ResponseBase;

public class TransitionEndResponse extends ResponseBase {
    private String name;

    private String type;

    @SerializedName("to-scene")
    private String toScene;

    private Integer duration;

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getToScene() {
        return toScene;
    }

    public Integer getDuration() {
        return duration;
    }
}
