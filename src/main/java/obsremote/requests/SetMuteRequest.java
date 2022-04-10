package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

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

