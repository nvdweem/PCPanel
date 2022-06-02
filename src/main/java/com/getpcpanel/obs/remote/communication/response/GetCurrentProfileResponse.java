package com.getpcpanel.obs.remote.communication.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetCurrentProfileResponse extends BaseResponse {
    @JsonProperty("profile-name")
    private String profileName;
}
