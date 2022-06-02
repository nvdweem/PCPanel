package com.getpcpanel.obs.remote.communication.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BaseResponse {
    @JsonProperty("message-id")
    private String messageId;

    private String status;

    private String error;

    public String getMessageId() {
        return messageId;
    }

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }
}

