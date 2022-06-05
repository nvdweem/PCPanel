package com.getpcpanel.profile;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.cpp.WindowFocusChangedEvent;
import com.getpcpanel.hid.DeviceHolder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileWindowFocusService {
    private final DeviceHolder devices;
    private String previousApplication = "";

    @EventListener
    public void onFocusChanged(WindowFocusChangedEvent event) {
        devices.values().forEach(d -> d.focusChanged(previousApplication, event.application()));
        previousApplication = event.application();
    }
}
