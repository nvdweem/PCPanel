package com.getpcpanel.obs.remote.communication.response;

import com.google.gson.annotations.SerializedName;

public class GetStreamingStatusResponse extends BaseResponse {
    private boolean streaming;

    private boolean recording;

    @SerializedName("stream-timecode")
    private String streamTimecode;

    @SerializedName("rec-timecode")
    private String recTimecode;

    public boolean isStreaming() {
        return streaming;
    }

    public boolean isRecording() {
        return recording;
    }

    public String getStreamTimecode() {
        return streamTimecode;
    }

    public String getRecTimecode() {
        return recTimecode;
    }
}
