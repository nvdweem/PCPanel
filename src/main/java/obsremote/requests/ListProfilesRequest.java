package obsremote.requests;

import obsremote.OBSCommunicator;

public class ListProfilesRequest extends BaseRequest {
    public ListProfilesRequest(OBSCommunicator com) {
        super(com, RequestType.ListProfiles);
    }
}

