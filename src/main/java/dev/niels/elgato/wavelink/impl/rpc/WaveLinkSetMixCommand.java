package dev.niels.elgato.wavelink.impl.rpc;

import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;

public class WaveLinkSetMixCommand extends WaveLinkJsonRpcCommand<WaveLinkMix, WaveLinkMix> {
    @Override
    public Class<WaveLinkMix> getResultClass() {
        return WaveLinkMix.class;
    }
}
