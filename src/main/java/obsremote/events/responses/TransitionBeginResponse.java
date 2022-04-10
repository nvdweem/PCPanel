package obsremote.events.responses;

import com.google.gson.annotations.SerializedName;

import obsremote.requests.ResponseBase;

public class TransitionBeginResponse extends ResponseBase {
    private String name;

    private String type;

    @SerializedName("from-scene")
    private String fromScene;

    @SerializedName("to-scene")
    private String toScene;

    private Integer duration;

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getFromScene() {
        return fromScene;
    }

    public String getToScene() {
        return toScene;
    }

    public Integer getDuration() {
        return duration;
    }
}
