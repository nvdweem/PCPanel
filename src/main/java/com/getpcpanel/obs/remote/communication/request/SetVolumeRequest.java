package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetVolumeRequest extends BaseRequest {
    private final String source;

    private final double volume;
    private final boolean useDecibel = true;

    public SetVolumeRequest(OBSCommunicator com, String source, double volume) {
        super(com, RequestType.SetVolume);
        this.source = source;
        this.volume = volume;
    }
}
