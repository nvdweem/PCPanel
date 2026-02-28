package dev.niels.elgato.wavelink.impl;

import java.util.concurrent.CompletionStage;

import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkAddToChannelParams;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetApplicationInfoResult;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetChannelsResult;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetInputDevicesResult;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetMixesResult;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkGetOutputDevicesResult;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkSetOutputDeviceParams;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkSetSubscriptionParams;

public interface WaveLinkRpcCommands {
    CompletionStage<WaveLinkGetApplicationInfoResult> getApplicationInfo();

    CompletionStage<WaveLinkGetInputDevicesResult> getInputDevices();

    CompletionStage<WaveLinkGetOutputDevicesResult> getOutputDevices();

    CompletionStage<WaveLinkGetChannelsResult> getChannels();

    CompletionStage<WaveLinkGetMixesResult> getMixes();

    CompletionStage<WaveLinkSetSubscriptionParams> setSubscription(WaveLinkSetSubscriptionParams params);

    CompletionStage<WaveLinkChannel> setChannel(WaveLinkChannel channel);

    void addToChannel(WaveLinkAddToChannelParams param);

    void setMix(WaveLinkMix mix);

    void setOutputDevice(WaveLinkSetOutputDeviceParams params);
}
