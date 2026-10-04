package com.getpcpanel.integration.display.platform.mac;

import java.time.Duration;

import com.getpcpanel.integration.display.DisplayPower;
import com.getpcpanel.platform.MacBuild;
import com.getpcpanel.util.os.ProcessHelper;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/** {@code pmset displaysleepnow}. */
@Log4j2
@Unremovable
@MacBuild
@ApplicationScoped
class MacDisplayPower implements DisplayPower {
    @Inject ProcessHelper processes;

    @Override
    public void turnOff() {
        try {
            if (!processes.run(Duration.ofSeconds(3), "pmset", "displaysleepnow").succeeded()) {
                log.warn("pmset displaysleepnow failed");
            }
        } catch (Exception e) {
            log.warn("Unable to turn the displays off", e);
        }
    }
}
