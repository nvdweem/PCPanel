package obsremote.requests;

import com.google.gson.annotations.SerializedName;

import lombok.Data;
import obsremote.OBSCommunicator;

@Data
public class BaseRequest {
    @SerializedName("request-type") private RequestType requestType;
    @SerializedName("message-id") private String messageId;
    private static int lastId;

    public BaseRequest(OBSCommunicator com, RequestType type) {
        lastId++;
        messageId = String.valueOf(lastId);
        requestType = type;
        com.messageTypes.put(getMessageId(), getClass());
    }
}
