package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class ToggleMuteRequest extends BaseRequest {
    private final String source;

    public ToggleMuteRequest(OBSCommunicator com, String source) {
        super(com, RequestType.ToggleMute);
        this.source = source;
    }
}

