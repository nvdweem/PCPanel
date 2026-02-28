package dev.niels.elgato.wavelink;

import dev.niels.elgato.wavelink.impl.WaveLinkClientImpl;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class WaveLinkClient extends WaveLinkClientImpl {
    public WaveLinkClient(boolean autoConnect) {
        super(autoConnect);
    }
}
