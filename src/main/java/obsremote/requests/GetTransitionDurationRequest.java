package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetTransitionDurationRequest extends BaseRequest {
    public GetTransitionDurationRequest(OBSCommunicator com) {
        super(com, RequestType.GetTransitionDuration);
    }
}

