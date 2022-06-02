package com.getpcpanel.obs.remote.communication.response;

import lombok.Data;

@Data
public class GetVolumeResponse extends BaseResponse {
    private String name;
    private double volume;
    private boolean muted;
}

