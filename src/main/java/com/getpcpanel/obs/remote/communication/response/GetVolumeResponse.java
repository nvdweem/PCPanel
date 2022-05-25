package com.getpcpanel.obs.remote.communication.response;

public class GetVolumeResponse extends BaseResponse {
    private String name;

    private double volume;

    private boolean muted;

    public String getName() {
        return name;
    }

    public double getVolume() {
        return volume;
    }

    public boolean isMuted() {
        return muted;
    }
}

