package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetSourceListRequest extends BaseRequest {
    public GetSourceListRequest(OBSCommunicator com) {
        super(com, RequestType.GetSourcesList);
    }
}
