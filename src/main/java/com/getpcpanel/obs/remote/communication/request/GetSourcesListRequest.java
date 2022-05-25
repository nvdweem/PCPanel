package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetSourcesListRequest extends BaseRequest {
    public GetSourcesListRequest(OBSCommunicator com) {
        super(com, RequestType.GetSourcesList);
    }
}
