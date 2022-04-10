package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class GetVolumeRequest extends BaseRequest {
    private final String source;

    public GetVolumeRequest(OBSCommunicator com, String name) {
        super(com, RequestType.GetVolume);
        source = name;
    }
}

