package dev.niels.elgato.wavelink.impl;

import dev.niels.elgato.jsonrpc.JsonRpcService;
import dev.niels.elgato.wavelink.impl.model.WaveLinkApp;
import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkChannelsChangedCommandResult;

@SuppressWarnings("unused") // Called via reflection
interface IWaveLinkService extends JsonRpcService {
    void channelChanged(WaveLinkChannel param);

    void channelsChanged(WaveLinkChannelsChangedCommandResult param);

    void outputDeviceChanged(WaveLinkOutputDevice param);

    void mixChanged(WaveLinkMix param);

    void focusedAppChanged(WaveLinkApp param);
}
