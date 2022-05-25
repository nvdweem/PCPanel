package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetVersionRequest extends BaseRequest {
    public GetVersionRequest(OBSCommunicator com) {
        super(com, RequestType.GetVersion);
    }
}

