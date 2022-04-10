package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetTransitionListRequest extends BaseRequest {
    public GetTransitionListRequest(OBSCommunicator com) {
        super(com, RequestType.GetTransitionList);
    }
}
