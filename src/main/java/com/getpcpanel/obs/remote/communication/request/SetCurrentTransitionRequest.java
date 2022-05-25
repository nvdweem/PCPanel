package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class SetCurrentTransitionRequest extends BaseRequest {
    @SerializedName("transition-name")
    private String transition;

    public SetCurrentTransitionRequest(OBSCommunicator com, String transition) {
        super(com, RequestType.SetCurrentTransition);
        this.transition = transition;
    }
}

