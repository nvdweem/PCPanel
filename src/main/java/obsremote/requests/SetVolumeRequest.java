package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class SetVolumeRequest extends BaseRequest {
    private final String source;

    private final double volume;

    public SetVolumeRequest(OBSCommunicator com, String source, double volume) {
        super(com, RequestType.SetVolume);
        this.source = source;
        this.volume = volume;
    }
}

