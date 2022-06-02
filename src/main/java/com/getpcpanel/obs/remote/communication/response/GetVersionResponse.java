package com.getpcpanel.obs.remote.communication.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetVersionResponse extends BaseResponse {
    @JsonProperty("obs-websocket-version") private String obsWebsocketVersion;
    @JsonProperty("obs-studio-version") private String obsStudioVersion;
}

