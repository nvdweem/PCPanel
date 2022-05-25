package com.getpcpanel.obs.remote.communication.response;

import com.google.gson.annotations.SerializedName;

public class GetVersionResponse extends BaseResponse {
    @SerializedName("obs-websocket-version")
    private String obsWebsocketVersion;

    @SerializedName("obs-studio-version")
    private String obsStudioVersion;

    public String getObsWebsocketVersion() {
        return obsWebsocketVersion;
    }

    public String getObsStudioVersion() {
        return obsStudioVersion;
    }
}

