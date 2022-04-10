package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetVersionRequest extends BaseRequest {
    public GetVersionRequest(OBSCommunicator com) {
        super(com, RequestType.GetVersion);
    }
}

