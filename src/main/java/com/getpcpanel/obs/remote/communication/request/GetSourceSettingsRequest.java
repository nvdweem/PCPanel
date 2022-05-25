package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class GetSourceSettingsRequest extends BaseRequest {
    private final String sourceName;

    public GetSourceSettingsRequest(OBSCommunicator com, String sourceName) {
        super(com, RequestType.GetSourceSettings);
        this.sourceName = sourceName;
    }
}
