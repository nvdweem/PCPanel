package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetCurrentProfileRequest extends BaseRequest {
    public GetCurrentProfileRequest(OBSCommunicator com) {
        super(com, RequestType.GetCurrentProfile);
    }
}
