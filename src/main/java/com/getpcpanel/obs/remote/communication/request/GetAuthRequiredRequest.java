package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetAuthRequiredRequest extends BaseRequest {
    public GetAuthRequiredRequest(OBSCommunicator com) {
        super(com, RequestType.GetAuthRequired);
    }
}
