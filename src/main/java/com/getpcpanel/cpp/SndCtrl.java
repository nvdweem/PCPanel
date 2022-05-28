package com.getpcpanel.cpp;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.log4j.Log4j2;

@Log4j2
@SuppressWarnings("ALL") // Methods are called from JNI
public enum SndCtrl {
    instance;

    static {
        System.loadLibrary("SndCtrl");
        instance.start();
    }

    private Map<DefaultFor, String> defaults = new HashMap<>();
    private List<AudioDevice> devices = new CopyOnWriteArrayList<>();

    public static Collection<AudioDevice> getDevices() {
        return Collections.unmodifiableCollection(instance.devices);
    }

    private native void start();

    private AudioDevice deviceAdded(String name, String id, float volume, boolean muted, int dataFlow) {
        var result = new AudioDevice(name, id).volume(volume).muted(muted).dataflow(dataFlow);
        devices.add(result);
        log.debug("{}", result);

        return result;
    }

    private void deviceRemoved(String id) {
        devices.removeIf(d -> id.equals(d.id()));
    }

    private void setDefaultDevice(String id, int dataFlow, int role) {
        defaults.put(new DefaultFor(dataFlow, role), id);
        log.debug("Default changed: {}: {}", new DefaultFor(dataFlow, role), id);
    }

    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(1000);
    }

    record DefaultFor(int dataFlow, int role) {
    }
}
