package com.getpcpanel.obs.remote.communication.request;

import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

@Data
public class BaseRequest {
    @SerializedName("request-type") private RequestType requestType;
    @SerializedName("message-id") private String messageId;
    private static int lastId;

    public BaseRequest(OBSCommunicator com, RequestType type) {
        lastId++;
        messageId = String.valueOf(lastId);
        requestType = type;
        com.messageTypes.put(messageId, type);
    }
}
