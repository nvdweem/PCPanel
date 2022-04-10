package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class GetSourceSettingsRequest extends BaseRequest {
    private final String sourceName;

    public GetSourceSettingsRequest(OBSCommunicator com, String sourceName) {
        super(com, RequestType.GetSourceSettings);
        this.sourceName = sourceName;
    }
}
