package com.getpcpanel.obs.remote.communication.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetStudioModeStatusResponse extends BaseResponse {
    @JsonProperty("studio-mode")
    private boolean enabled;
}

