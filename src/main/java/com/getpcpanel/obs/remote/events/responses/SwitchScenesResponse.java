package com.getpcpanel.obs.remote.events.responses;

import java.util.List;

import com.getpcpanel.obs.remote.communication.response.BaseResponse;
import com.getpcpanel.obs.remote.objects.Source;
import com.google.gson.annotations.SerializedName;

public class SwitchScenesResponse extends BaseResponse {
    @SerializedName("scene-name")
    private String sceneName;

    private List<Source> sources;

    public String getSceneName() {
        return sceneName;
    }

    public List<Source> getSources() {
        return sources;
    }
}
