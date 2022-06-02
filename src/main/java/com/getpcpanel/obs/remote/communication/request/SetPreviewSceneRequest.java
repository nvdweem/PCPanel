package com.getpcpanel.obs.remote.communication.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetPreviewSceneRequest extends BaseRequest {
    @JsonProperty("scene-name")
    private String sceneName;

    public SetPreviewSceneRequest(OBSCommunicator com, String name) {
        super(com, RequestType.SetPreviewScene);
        sceneName = name;
    }
}

