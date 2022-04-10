package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetSceneListRequest extends BaseRequest {
    public GetSceneListRequest(OBSCommunicator com) {
        super(com, RequestType.GetSceneList);
    }
}
