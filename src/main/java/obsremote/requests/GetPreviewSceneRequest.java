package obsremote.requests;

import obsremote.OBSCommunicator;

public class GetPreviewSceneRequest extends BaseRequest {
    public GetPreviewSceneRequest(OBSCommunicator com) {
        super(com, RequestType.GetPreviewScene);
    }
}
