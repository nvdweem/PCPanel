package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetCurrentSceneRequest extends BaseRequest {
    public GetCurrentSceneRequest(OBSCommunicator com) {
        super(com, RequestType.GetCurrentScene);
    }
}
