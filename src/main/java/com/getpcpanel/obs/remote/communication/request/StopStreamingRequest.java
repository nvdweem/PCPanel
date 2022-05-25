package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class StopStreamingRequest extends BaseRequest {
    public StopStreamingRequest(OBSCommunicator com) {
        super(com, RequestType.StopStreaming);
    }
}

