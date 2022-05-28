package com.getpcpanel.cpp;

enum SndCtrlNative {
    instance;

    public static native void start(Object target);

    public native void setDeviceVolume(String id, float volume);

    public native void setProcessVolume(String deviceId, int pid, float volume);

    public native void setFocusVolume(float volume);
}
