package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetCurrentSceneRequest extends BaseRequest {
    public GetCurrentSceneRequest(OBSCommunicator com) {
        super(com, RequestType.GetCurrentScene);
    }
}
