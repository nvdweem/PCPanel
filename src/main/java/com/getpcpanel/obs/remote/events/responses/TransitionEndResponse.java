package com.getpcpanel.obs.remote.events.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.communication.response.BaseResponse;

import lombok.Data;

@Data
public class TransitionEndResponse extends BaseResponse {
    private String name;
    private String type;
    @JsonProperty("to-scene") private String toScene;
    private Integer duration;
}
