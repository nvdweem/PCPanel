package obsremote.requests;

import java.util.Map;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class SetSourceSettingsRequest extends BaseRequest {
    private final String sourceName;

    private final Map<String, Object> sourceSettings;

    public SetSourceSettingsRequest(OBSCommunicator com, String sourceName, Map<String, Object> settings) {
        super(com, RequestType.SetSourceSettings);
        this.sourceName = sourceName;
        sourceSettings = settings;
    }
}

