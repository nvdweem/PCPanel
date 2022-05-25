package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class SetCurrentProfileRequest extends BaseRequest {
    @SerializedName("profile-name")
    private String profileName;

    public SetCurrentProfileRequest(OBSCommunicator com, String profileName) {
        super(com, RequestType.SetCurrentProfile);
        this.profileName = profileName;
    }
}

