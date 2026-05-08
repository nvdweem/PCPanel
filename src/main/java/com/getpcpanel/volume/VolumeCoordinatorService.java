package com.getpcpanel.volume;

import static com.getpcpanel.volume.FocusVolumeEventType.process;
import static com.getpcpanel.volume.FocusVolumeEventType.wavelinkChannel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.volume.FocusVolumeEvent.FocusVolumeTarget;
import com.getpcpanel.wavelink.WaveLinkService;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Unremovable
@ApplicationScoped
public class VolumeCoordinatorService {
    @Inject ISndCtrl sndCtrl;
    @Inject WaveLinkService waveLinkService;
    private final List<FocusVolumeListener> focusVolumeListeners = new CopyOnWriteArrayList<>();

    public void setFocusVolume(double value) {
        var channelId = waveLinkService.findChannelIdForFocusApp();
        var floatValue = (float) value;
        if (channelId.isPresent()) {
            notifyFocusVolumeListeners(new FocusVolumeEvent(new FocusVolumeTarget(wavelinkChannel, channelId.get()), floatValue));
            waveLinkService.setChannelLevel(channelId.get(), value);
        } else {
            notifyFocusVolumeListeners(new FocusVolumeEvent(new FocusVolumeTarget(process, sndCtrl.getFocusApplication()), floatValue));
            sndCtrl.setFocusVolume(floatValue);
        }
    }

    public void addFocusVolumeListener(FocusVolumeListener listener) {
        focusVolumeListeners.add(listener);
    }

    public void removeFocusVolumeListener(FocusVolumeListener listener) {
        focusVolumeListeners.remove(listener);
    }

    private void notifyFocusVolumeListeners(FocusVolumeEvent event) {
        focusVolumeListeners.forEach(l -> l.onFocusVolumeChange(event));
    }
}
