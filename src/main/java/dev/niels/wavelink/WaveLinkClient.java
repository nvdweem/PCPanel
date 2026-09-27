package dev.niels.wavelink;

import java.net.http.HttpClient;

import dev.niels.wavelink.impl.WaveLinkClientImpl;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class WaveLinkClient extends WaveLinkClientImpl {
    public WaveLinkClient(boolean autoConnect) {
        super(autoConnect);
    }

    public WaveLinkClient(HttpClient client, boolean autoConnect) {
        super(client, autoConnect);
    }
}
