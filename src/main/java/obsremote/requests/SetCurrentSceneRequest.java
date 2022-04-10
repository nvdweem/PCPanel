package obsremote.requests;

import com.google.gson.annotations.SerializedName;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class SetCurrentSceneRequest extends BaseRequest {
    @SerializedName("scene-name")
    private String scene;

    public SetCurrentSceneRequest(OBSCommunicator com, String scene) {
        super(com, RequestType.SetCurrentScene);
        this.scene = scene;
    }
}

