package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class SetTransitionDurationRequest extends BaseRequest {
    private final int duration;

    public SetTransitionDurationRequest(OBSCommunicator com, int duration) {
        super(com, RequestType.SetTransitionDuration);
        this.duration = duration;
    }
}

