package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetSourceTypeListRequest extends BaseRequest {
    public GetSourceTypeListRequest(OBSCommunicator com) {
        super(com, RequestType.GetSourceTypesList);
    }
}

