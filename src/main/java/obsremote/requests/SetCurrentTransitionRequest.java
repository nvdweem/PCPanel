package obsremote.requests;

import com.google.gson.annotations.SerializedName;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class SetCurrentTransitionRequest extends BaseRequest {
    @SerializedName("transition-name")
    private String transition;

    public SetCurrentTransitionRequest(OBSCommunicator com, String transition) {
        super(com, RequestType.SetCurrentTransition);
        this.transition = transition;
    }
}

