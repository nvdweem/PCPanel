package com.getpcpanel.obs.remote.events.responses;

import com.getpcpanel.obs.remote.communication.response.BaseResponse;
import com.google.gson.annotations.SerializedName;

public class TransitionEndResponse extends BaseResponse {
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
