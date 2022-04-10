package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetStreamingStatusRequest extends BaseRequest {
    public GetStreamingStatusRequest(OBSCommunicator com) {
        super(com, RequestType.GetStreamingStatus);
    }
}

