package com.getpcpanel.cpp;

enum SndCtrlNative {
    instance;

    public static native void start(Object target);

    public native void setDeviceVolume(String id, float volume);

    public native void setProcessVolume(String deviceId, int pid, float volume);

    public native void setFocusVolume(float volume);

    public native void setDefaultDevice(String device, int dataFlow, int role);

    public native void muteDevice(String deviceId, boolean muted);

    public native void muteSession(String id, int pid, boolean muted);

    public native String getFocusApplication();

    public native String[] getAllRunningProcesses();
}
