package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class SetPreviewSceneRequest extends BaseRequest {
    @SerializedName("scene-name")
    private String sceneName;

    public SetPreviewSceneRequest(OBSCommunicator com, String name) {
        super(com, RequestType.SetPreviewScene);
        sceneName = name;
    }
}

