package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetAuthRequiredRequest extends BaseRequest {
    public GetAuthRequiredRequest(OBSCommunicator com) {
        super(com, RequestType.GetAuthRequired);
    }
}
