package obsremote.requests;

import obsremote.OBSCommunicator;

public class StartReplayBufferRequest extends BaseRequest {
    public StartReplayBufferRequest(OBSCommunicator com) {
        super(com, RequestType.StartReplayBuffer);
    }
}

