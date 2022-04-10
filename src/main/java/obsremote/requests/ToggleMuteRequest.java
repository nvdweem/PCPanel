package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class ToggleMuteRequest extends BaseRequest {
    private final String source;

    public ToggleMuteRequest(OBSCommunicator com, String source) {
        super(com, RequestType.ToggleMute);
        this.source = source;
    }
}

