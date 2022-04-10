package obsremote.requests;

import obsremote.OBSCommunicator;

public class SaveReplayBufferRequest extends BaseRequest {
    public SaveReplayBufferRequest(OBSCommunicator com) {
        super(com, RequestType.SaveReplayBuffer);
    }
}

