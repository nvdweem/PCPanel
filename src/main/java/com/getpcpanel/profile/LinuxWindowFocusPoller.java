package com.getpcpanel.profile;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.cpp.windows.WindowFocusChangedEvent;
import com.getpcpanel.platform.LinuxBuild;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Bridges window-focus changes into the CDI event bus on Linux. Windows fires
 * {@link WindowFocusChangedEvent} from a native {@code SetWinEventHook} callback; Linux has no
 * such OS push, so this poller samples the focused application and fires the event when it
 * changes. That is what makes per-profile "activate this profile when application X is focused"
 * actually work on Linux ({@code getFocusApplication()} resolves via xdotool/kdotool).
 */
@Log4j2
@ApplicationScoped
@LinuxBuild
public class LinuxWindowFocusPoller {
    @Inject ISndCtrl sndCtrl;
    @Inject Event<Object> eventBus;

    private String last = "";

    @Scheduled(every = "1s")
    void poll() {
        var current = sndCtrl.getFocusApplication();
        if (StringUtils.equals(current, last)) {
            return;
        }
        last = current;
        log.trace("Focused application changed to {}", current);
        eventBus.fire(new WindowFocusChangedEvent(current));
    }
}
