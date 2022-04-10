package obsremote.requests;

import obsremote.OBSCommunicator;

public class SetStudioModeEnabledRequest extends BaseRequest {
    public SetStudioModeEnabledRequest(OBSCommunicator com, boolean enabled) {
        super(com, enabled ? RequestType.EnableStudioMode : RequestType.DisableStudioMode);
    }
}

