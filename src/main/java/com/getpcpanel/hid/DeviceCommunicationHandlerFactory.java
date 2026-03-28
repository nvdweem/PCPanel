package com.getpcpanel.hid;

import org.hid4java.HidDevice;

import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class DeviceCommunicationHandlerFactory {
    private final Event<Object> eventPublisher;
    private final DeviceScanner deviceScanner;
    private final SaveService saveService;

    public DeviceCommunicationHandler build(String key, HidDevice device, DeviceType deviceType) {
        return new DeviceCommunicationHandler(deviceScanner, eventPublisher, saveService, key, device, deviceType);
    }
}
