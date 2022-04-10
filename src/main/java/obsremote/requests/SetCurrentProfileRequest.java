package obsremote.requests;

import com.google.gson.annotations.SerializedName;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class SetCurrentProfileRequest extends BaseRequest {
    @SerializedName("profile-name")
    private String profileName;

    public SetCurrentProfileRequest(OBSCommunicator com, String profileName) {
        super(com, RequestType.SetCurrentProfile);
        this.profileName = profileName;
    }
}

