package obsremote.requests;

import obsremote.OBSCommunicator;

public class StartRecordingRequest extends BaseRequest {
    public StartRecordingRequest(OBSCommunicator com) {
        super(com, RequestType.StartRecording);
    }
}

