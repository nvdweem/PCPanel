package com.getpcpanel.hid;

import org.hid4java.HidDevice;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.getpcpanel.profile.SaveService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class DeviceCommunicationHandlerFactory {
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceScanner deviceScanner;
    private final SaveService saveService;

    public DeviceCommunicationHandler build(String key, HidDevice device) {
        return new DeviceCommunicationHandler(deviceScanner, eventPublisher, saveService, key, device);
    }
}
