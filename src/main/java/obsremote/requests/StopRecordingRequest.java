package obsremote.requests;

import obsremote.OBSCommunicator;

public class StopRecordingRequest extends BaseRequest {
    public StopRecordingRequest(OBSCommunicator com) {
        super(com, RequestType.StopRecording);
    }
}

