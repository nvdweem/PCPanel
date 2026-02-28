package dev.niels.elgato.wavelink.impl.rpc;

import dev.niels.elgato.wavelink.impl.rpc.WaveLinkAddToChannelCommand.WaveLinkAddToChannelParams;

public class WaveLinkAddToChannelCommand extends WaveLinkJsonRpcCommand<WaveLinkAddToChannelParams, Void> {
    public record WaveLinkAddToChannelParams(String appId, String channelId) {
    }
}
