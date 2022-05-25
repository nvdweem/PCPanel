package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetCurrentProfileRequest extends BaseRequest {
    public GetCurrentProfileRequest(OBSCommunicator com) {
        super(com, RequestType.GetCurrentProfile);
    }
}
