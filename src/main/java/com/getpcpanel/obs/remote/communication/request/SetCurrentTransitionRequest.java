package com.getpcpanel.obs.remote.communication.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetCurrentTransitionRequest extends BaseRequest {
    @JsonProperty("transition-name")
    private String transition;

    public SetCurrentTransitionRequest(OBSCommunicator com, String transition) {
        super(com, RequestType.SetCurrentTransition);
        this.transition = transition;
    }
}

