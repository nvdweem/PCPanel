package com.getpcpanel.obs.remote.communication.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetSceneItemPropertiesRequest extends BaseRequest {
    @JsonProperty("scene-name")
    private String scene;
    private final String item;
    private final boolean visible;

    public SetSceneItemPropertiesRequest(OBSCommunicator com, String scene, String source, boolean visible) {
        super(com, RequestType.SetSceneItemProperties);
        this.scene = scene;
        item = source;
        this.visible = visible;
    }
}

