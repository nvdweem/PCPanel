package com.getpcpanel.volume;

import java.util.Optional;

import com.getpcpanel.cpp.ISndCtrl;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Unremovable
@ApplicationScoped
public class VolumeCoordinatorService {
    @Inject ISndCtrl sndCtrl;
    @Inject Instance<IFocusRedirector> focusRedirectors;

    public void setFocusVolume(double value) {
        var application = sndCtrl.getFocusApplication();
        var floatValue = (float) value;

        StreamEx.of(focusRedirectors.handlesStream())
                .findFirst(fr -> fr.get().handleFocusVolumeRequest(application, floatValue))
                .ifPresentOrElse(
                        handler -> log.trace("Focus volume request for {} handled by {}", application, handler.getClass().getSimpleName()),
                        () -> sndCtrl.setFocusVolume(floatValue)
                );
    }

    /**
     * The redirector that would claim {@code application}'s focus volume, or empty when none would (the
     * OS controls it directly). Side-effect-free — evaluates the deferral decision without changing any
     * volume. Used by the dev test harness to inspect where focused-app volume goes.
     */
    public Optional<String> focusVolumeTarget(String application) {
        return StreamEx.of(focusRedirectors.handlesStream())
                .findFirst(fr -> fr.get().controlsFocusApp(application))
                .map(fr -> fr.get().getClass().getSimpleName());
    }
}
