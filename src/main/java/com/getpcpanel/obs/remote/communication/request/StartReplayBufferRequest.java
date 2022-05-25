package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class StartReplayBufferRequest extends BaseRequest {
    public StartReplayBufferRequest(OBSCommunicator com) {
        super(com, RequestType.StartReplayBuffer);
    }
}

