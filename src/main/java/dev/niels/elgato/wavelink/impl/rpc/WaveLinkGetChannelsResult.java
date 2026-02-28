package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;

public record WaveLinkGetChannelsResult(
        List<WaveLinkChannel> channels
) {
}
