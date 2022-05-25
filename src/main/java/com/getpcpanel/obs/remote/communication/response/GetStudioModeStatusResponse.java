package com.getpcpanel.obs.remote.communication.response;

import com.google.gson.annotations.SerializedName;

public class GetStudioModeStatusResponse extends BaseResponse {
    @SerializedName("studio-mode")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }
}

