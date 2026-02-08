package dev.niels.wavelink.impl.rpc;

import dev.niels.wavelink.impl.model.WaveLinkChannel;

public class WaveLinkSetChannelCommand extends WaveLinkJsonRpcCommand<WaveLinkChannel, WaveLinkChannel> {
    @Override
    public Class<WaveLinkChannel> getResultClass() {
        return WaveLinkChannel.class;
    }
}
