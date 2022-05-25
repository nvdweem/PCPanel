package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class StopReplayBufferRequest extends BaseRequest {
    public StopReplayBufferRequest(OBSCommunicator com) {
        super(com, RequestType.StopReplayBuffer);
    }
}

