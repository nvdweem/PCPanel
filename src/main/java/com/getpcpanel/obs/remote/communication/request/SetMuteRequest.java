package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetMuteRequest extends BaseRequest {
    private final boolean mute;

    private final String source;

    public SetMuteRequest(OBSCommunicator com, String source, boolean mute) {
        super(com, RequestType.SetMute);
        this.mute = mute;
        this.source = source;
    }
}

