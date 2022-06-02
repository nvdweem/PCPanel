package com.getpcpanel.obs.remote.communication.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetTransitionDurationResponse extends BaseResponse {
    @JsonProperty("transition-duration")
    private int transitionDuration;
}

