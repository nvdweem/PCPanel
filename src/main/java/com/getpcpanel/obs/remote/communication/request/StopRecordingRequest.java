package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class StopRecordingRequest extends BaseRequest {
    public StopRecordingRequest(OBSCommunicator com) {
        super(com, RequestType.StopRecording);
    }
}

