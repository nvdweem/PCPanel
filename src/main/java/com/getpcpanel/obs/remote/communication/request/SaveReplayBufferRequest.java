package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class SaveReplayBufferRequest extends BaseRequest {
    public SaveReplayBufferRequest(OBSCommunicator com) {
        super(com, RequestType.SaveReplayBuffer);
    }
}
