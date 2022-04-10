package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetStudioModeEnabledRequest extends BaseRequest {
    public GetStudioModeEnabledRequest(OBSCommunicator com) {
        super(com, RequestType.GetStudioModeStatus);
    }
}

