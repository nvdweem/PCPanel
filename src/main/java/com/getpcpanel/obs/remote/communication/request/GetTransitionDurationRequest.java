package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetTransitionDurationRequest extends BaseRequest {
    public GetTransitionDurationRequest(OBSCommunicator com) {
        super(com, RequestType.GetTransitionDuration);
    }
}

