package com.getpcpanel.volume;

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
}
