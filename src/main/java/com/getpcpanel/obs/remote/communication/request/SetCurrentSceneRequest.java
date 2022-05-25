package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class SetCurrentSceneRequest extends BaseRequest {
    @SerializedName("scene-name")
    private String scene;

    public SetCurrentSceneRequest(OBSCommunicator com, String scene) {
        super(com, RequestType.SetCurrentScene);
        this.scene = scene;
    }
}

