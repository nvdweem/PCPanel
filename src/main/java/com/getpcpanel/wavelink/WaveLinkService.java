package com.getpcpanel.wavelink;

import org.springframework.stereotype.Service;

import dev.niels.wavelink.IWaveLinkClient;
import dev.niels.wavelink.WaveLinkClient;
import lombok.experimental.Delegate;

@Service
public class WaveLinkService {
    @Delegate private final IWaveLinkClient client = new WaveLinkClient();

    public boolean isConnected() {
        return ((WaveLinkClient) client).isInitialized();
    }
}
