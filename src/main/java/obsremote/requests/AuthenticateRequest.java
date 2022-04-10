package obsremote.requests;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class AuthenticateRequest extends BaseRequest {
    private final String auth;

    public AuthenticateRequest(OBSCommunicator com, String auth) {
        super(com, RequestType.Authenticate);
        this.auth = auth;
    }
}
