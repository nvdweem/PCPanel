package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class GetVolumeRequest extends BaseRequest {
    private final String source;

    public GetVolumeRequest(OBSCommunicator com, String name) {
        super(com, RequestType.GetVolume);
        source = name;
    }
}

