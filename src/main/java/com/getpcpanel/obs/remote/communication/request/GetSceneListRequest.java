package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetSceneListRequest extends BaseRequest {
    public GetSceneListRequest(OBSCommunicator com) {
        super(com, RequestType.GetSceneList);
    }
}
