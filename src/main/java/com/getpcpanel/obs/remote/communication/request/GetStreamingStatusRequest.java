package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetStreamingStatusRequest extends BaseRequest {
    public GetStreamingStatusRequest(OBSCommunicator com) {
        super(com, RequestType.GetStreamingStatus);
    }
}

