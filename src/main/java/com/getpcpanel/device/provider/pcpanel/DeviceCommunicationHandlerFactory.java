package com.getpcpanel.device.provider.pcpanel;

import java.time.Duration;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hid4java.HidDevice;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.device.descriptor.DeviceDescriptor;
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
    @Inject
    LightingTrace trace;
    /** A panel can drop its LEDs while it keeps working; its last lighting is sent again after this long without one. */
    @ConfigProperty(name = "pcpanel.lighting.refresh-interval")
    Duration lightingRefresh;

    public DeviceCommunicationHandler build(String key, HidDevice device, DeviceDescriptor descriptor) {
        return new DeviceCommunicationHandler(deviceScanner, saveService, trace, eventBus, key, device, descriptor, lightingRefresh);
    }
}
