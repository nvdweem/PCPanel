package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

public class SetStudioModeEnabledRequest extends BaseRequest {
    public SetStudioModeEnabledRequest(OBSCommunicator com, boolean enabled) {
        super(com, enabled ? RequestType.EnableStudioMode : RequestType.DisableStudioMode);
    }
}

