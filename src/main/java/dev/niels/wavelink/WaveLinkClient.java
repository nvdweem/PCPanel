package dev.niels.wavelink;

import dev.niels.wavelink.impl.WaveLinkClientImpl;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class WaveLinkClient extends WaveLinkClientImpl {
    public WaveLinkClient(boolean autoConnect) {
        super(autoConnect);
    }
}
