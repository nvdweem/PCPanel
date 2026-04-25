package com.getpcpanel.hid;

import org.hid4java.HidDevice;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;

import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class DeviceCommunicationHandlerFactory {
    @Inject
    Event<Object> eventBus;
    @Inject
    DeviceScanner deviceScanner;
    @Inject
    SaveService saveService;

    public DeviceCommunicationHandler build(String key, HidDevice device, DeviceType deviceType) {
        return new DeviceCommunicationHandler(deviceScanner, saveService, eventBus, key, device, deviceType);
    }
}
