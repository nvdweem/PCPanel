package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class StartRecordingRequest extends BaseRequest {
    public StartRecordingRequest(OBSCommunicator com) {
        super(com, RequestType.StartRecording);
    }
}

