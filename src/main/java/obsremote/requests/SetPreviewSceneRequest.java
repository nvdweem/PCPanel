package obsremote.requests;

import com.google.gson.annotations.SerializedName;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class SetPreviewSceneRequest extends BaseRequest {
    @SerializedName("scene-name")
    private String sceneName;

    public SetPreviewSceneRequest(OBSCommunicator com, String name) {
        super(com, RequestType.SetPreviewScene);
        sceneName = name;
    }
}

