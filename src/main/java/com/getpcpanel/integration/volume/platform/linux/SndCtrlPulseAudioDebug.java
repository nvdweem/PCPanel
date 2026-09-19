package com.getpcpanel.integration.volume.platform.linux;

import com.getpcpanel.integration.clipboard.ClipboardWriter;
import com.getpcpanel.platform.LinuxBuild;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import one.util.streamex.StreamEx;

@ApplicationScoped
@LinuxBuild
class SndCtrlPulseAudioDebug {
    @Inject
    PulseAudioWrapper paWrapper;
    @Inject
    PulseAudioEventListener paEventListener;
    @Inject
    ClipboardWriter clipboard;

    public void copyDebugOutput() {
        var output = StreamEx.of(paWrapper.getDebugOutput())
                             .append(paEventListener.getDebugOutput())
                             .joining("\n".repeat(5));
        clipboard.setText(output);
    }
}
