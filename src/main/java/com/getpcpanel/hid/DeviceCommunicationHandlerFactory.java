package com.getpcpanel.hid;

import org.hid4java.HidDevice;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class DeviceCommunicationHandlerFactory {
    private final DeviceScanner deviceScanner;
    private final InputInterpreter inputInterpreter;

    public DeviceCommunicationHandler build(String key, HidDevice device) {
        return new DeviceCommunicationHandler(deviceScanner, inputInterpreter, key, device);
    }
}
