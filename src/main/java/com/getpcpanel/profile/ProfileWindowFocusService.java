package com.getpcpanel.profile;

import com.getpcpanel.cpp.windows.WindowFocusChangedEvent;
import com.getpcpanel.hid.DeviceHolder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class ProfileWindowFocusService {
    private final DeviceHolder devices;
    private String previousApplication = "";

    public void onFocusChanged(@Observes WindowFocusChangedEvent event) {
        devices.values().forEach(d -> d.focusChanged(previousApplication, event.application()));
        previousApplication = event.application();
    }
}
