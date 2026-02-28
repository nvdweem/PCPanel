package dev.niels.elgato.wavelink.impl.rpc;

import java.util.List;

import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetChannels.WaveLinkGetChannelsResult;

public class WaveLinkGetChannels extends WaveLinkJsonRpcCommand<Void, WaveLinkGetChannelsResult> {
    @Override
    public Class<WaveLinkGetChannelsResult> getResultClass() {
        return WaveLinkGetChannelsResult.class;
    }

    public record WaveLinkGetChannelsResult(
            List<WaveLinkChannel> channels
    ) {
    }

}
