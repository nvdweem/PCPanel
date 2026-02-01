package com.getpcpanel.wavelink;

import org.springframework.stereotype.Service;

import dev.niels.wavelink.WaveLinkClient;

@Service
public class WaveLinkService {
    public final WaveLinkClient client = new WaveLinkClient();
}
