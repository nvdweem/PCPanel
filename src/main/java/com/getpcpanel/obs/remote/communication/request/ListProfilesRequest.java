package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class ListProfilesRequest extends BaseRequest {
    public ListProfilesRequest(OBSCommunicator com) {
        super(com, RequestType.ListProfiles);
    }
}

