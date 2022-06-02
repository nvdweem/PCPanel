package com.getpcpanel.obs.remote.communication.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetCurrentSceneRequest extends BaseRequest {
    @JsonProperty("scene-name")
    private String scene;

    public SetCurrentSceneRequest(OBSCommunicator com, String scene) {
        super(com, RequestType.SetCurrentScene);
        this.scene = scene;
    }
}

