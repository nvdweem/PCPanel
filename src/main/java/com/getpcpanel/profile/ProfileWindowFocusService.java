package com.getpcpanel.profile;

import jakarta.inject.Inject;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.device.DeviceHolder;

import lombok.RequiredArgsConstructor;

@ApplicationScoped
public class ProfileWindowFocusService {
    @Inject
    DeviceHolder devices;
    private String previousApplication = "";

        public void onFocusChanged(@Observes WindowFocusChangedEvent event) {
        devices.values().forEach(d -> d.focusChanged(previousApplication, event.application()));
        previousApplication = event.application();
    }
}
