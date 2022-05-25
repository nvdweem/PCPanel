package com.getpcpanel.obs.remote.communication.response;

import com.google.gson.annotations.SerializedName;

public class GetTransitionDurationResponse extends BaseResponse {
    @SerializedName("transition-duration")
    private int transitionDuration;

    public int getTransitionDuration() {
        return transitionDuration;
    }
}

