package com.getpcpanel.volume;

public interface IFocusRedirector {
    boolean handleFocusVolumeRequest(String targetProcess, float volume);
}
