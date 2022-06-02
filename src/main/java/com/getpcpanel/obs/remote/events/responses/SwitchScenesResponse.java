package com.getpcpanel.obs.remote.events.responses;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.communication.response.BaseResponse;
import com.getpcpanel.obs.remote.objects.Source;

import lombok.Data;

@Data
public class SwitchScenesResponse extends BaseResponse {
    @JsonProperty("scene-name")
    private String sceneName;
    private List<Source> sources;
}
