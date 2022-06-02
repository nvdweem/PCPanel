package com.getpcpanel.obs.remote.communication.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetCurrentProfileRequest extends BaseRequest {
    @JsonProperty("profile-name")
    private String profileName;

    public SetCurrentProfileRequest(OBSCommunicator com, String profileName) {
        super(com, RequestType.SetCurrentProfile);
        this.profileName = profileName;
    }
}

