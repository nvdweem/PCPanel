package obsremote.requests;

import obsremote.OBSCommunicator;

public class StartStreamingRequest extends BaseRequest {
    public StartStreamingRequest(OBSCommunicator com) {
        super(com, RequestType.StartStreaming);
    }
}

