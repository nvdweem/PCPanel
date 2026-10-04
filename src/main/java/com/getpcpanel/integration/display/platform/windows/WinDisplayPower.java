package com.getpcpanel.integration.display.platform.windows;

import com.getpcpanel.integration.display.DisplayPower;
import com.getpcpanel.platform.WindowsBuild;
import com.getpcpanel.sleepdetection.WindowsSystemEventService;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Asks the sleep-detection helper window to put the monitors to sleep (see {@link WindowsSystemEventService#turnDisplaysOff()}). */
@Unremovable
@WindowsBuild
@ApplicationScoped
class WinDisplayPower implements DisplayPower {
    @Inject WindowsSystemEventService systemEvents;

    @Override
    public void turnOff() {
        systemEvents.turnDisplaysOff();
    }
}
