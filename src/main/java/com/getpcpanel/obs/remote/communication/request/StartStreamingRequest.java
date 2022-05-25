package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class StartStreamingRequest extends BaseRequest {
    public StartStreamingRequest(OBSCommunicator com) {
        super(com, RequestType.StartStreaming);
    }
}

