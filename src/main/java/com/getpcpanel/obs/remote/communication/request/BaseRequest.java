package com.getpcpanel.obs.remote.communication.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.obs.remote.OBSCommunicator;
import com.getpcpanel.obs.remote.communication.RequestType;

import lombok.Data;

@Data
public class BaseRequest {
    @JsonProperty("request-type") private RequestType requestType;
    @JsonProperty("message-id") private String messageId;
    private static int lastId;

    public BaseRequest(OBSCommunicator com, RequestType type) {
        lastId++;
        messageId = String.valueOf(lastId);
        requestType = type;
        com.messageTypes.put(messageId, type);
    }
}
