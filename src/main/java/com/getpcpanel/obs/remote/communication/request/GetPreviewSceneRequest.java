package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetPreviewSceneRequest extends BaseRequest {
    public GetPreviewSceneRequest(OBSCommunicator com) {
        super(com, RequestType.GetPreviewScene);
    }
}
