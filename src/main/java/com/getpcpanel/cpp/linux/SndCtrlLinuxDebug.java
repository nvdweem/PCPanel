package com.getpcpanel.cpp.linux;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

import org.springframework.stereotype.Service;

import com.getpcpanel.spring.ConditionalOnLinux;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@ConditionalOnLinux
@RequiredArgsConstructor
public class SndCtrlLinuxDebug {
    private final PulseAudioWrapper paWrapper;
    private final PulseAudioEventListener paEventListener;

    public void copyDebugOutput() {
        var output = StreamEx.of(paWrapper.getDebugOutput())
                             .append(paEventListener.getDebugOutput())
                             .joining("\n".repeat(5));
        var content = new StringSelection(output);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, null);
    }
}
