package com.getpcpanel.obs.remote.communication.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetStreamingStatusResponse extends BaseResponse {
    private boolean streaming;
    private boolean recording;
    @JsonProperty("stream-timecode") private String streamTimecode;
    @JsonProperty("rec-timecode") private String recTimecode;
}

