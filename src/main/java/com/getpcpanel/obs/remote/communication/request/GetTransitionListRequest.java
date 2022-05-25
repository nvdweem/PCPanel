package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetTransitionListRequest extends BaseRequest {
    public GetTransitionListRequest(OBSCommunicator com) {
        super(com, RequestType.GetTransitionList);
    }
}
