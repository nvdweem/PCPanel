package com.getpcpanel.obs.remote.communication.response;

import com.google.gson.annotations.SerializedName;

public class BaseResponse {
    @SerializedName("message-id")
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

