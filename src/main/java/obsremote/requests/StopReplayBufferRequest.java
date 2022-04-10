package obsremote.requests;

import obsremote.OBSCommunicator;

public class StopReplayBufferRequest extends BaseRequest {
    public StopReplayBufferRequest(OBSCommunicator com) {
        super(com, RequestType.StopReplayBuffer);
    }
}

