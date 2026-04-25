package com.getpcpanel.cpp.linux.pulseaudio;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@PulseAudioImpl
public class SndCtrlPulseAudioDebug {
    @Inject @PulseAudioImpl
    PulseAudioWrapper paWrapper;
    @Inject @PulseAudioImpl
    PulseAudioEventListener paEventListener;

    public void copyDebugOutput() {
        var output = StreamEx.of(paWrapper.getDebugOutput())
                             .append(paEventListener.getDebugOutput())
                             .joining("\n".repeat(5));
        var content = new StringSelection(output);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, null);
    }
}
