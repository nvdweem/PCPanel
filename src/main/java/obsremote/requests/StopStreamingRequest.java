package obsremote.requests;

import obsremote.OBSCommunicator;

public class StopStreamingRequest extends BaseRequest {
    public StopStreamingRequest(OBSCommunicator com) {
        super(com, RequestType.StopStreaming);
    }
}

