package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class SetTransitionDurationRequest extends BaseRequest {
    private final int duration;

    public SetTransitionDurationRequest(OBSCommunicator com, int duration) {
        super(com, RequestType.SetTransitionDuration);
        this.duration = duration;
    }
}

