package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetStudioModeStatusRequest extends BaseRequest {
    public GetStudioModeStatusRequest(OBSCommunicator com) {
        super(com, RequestType.GetStudioModeStatus);
    }
}

