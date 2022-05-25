package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class GetSourceTypesListRequest extends BaseRequest {
    public GetSourceTypesListRequest(OBSCommunicator com) {
        super(com, RequestType.GetSourceTypesList);
    }
}

