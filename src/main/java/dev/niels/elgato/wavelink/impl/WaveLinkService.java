package dev.niels.elgato.wavelink.impl;

import dev.niels.elgato.wavelink.impl.model.WaveLinkApp;
import dev.niels.elgato.wavelink.impl.model.WaveLinkChannel;
import dev.niels.elgato.wavelink.impl.model.WaveLinkMix;
import dev.niels.elgato.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.elgato.wavelink.impl.rpc.WaveLinkChannelsChangedCommand.WaveLinkChannelsChangedCommandResult;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class WaveLinkService {
    public void channelChanged(WaveLinkChannel param) {
        System.out.println(param);
    }

    public void channelsChanged(WaveLinkChannelsChangedCommandResult param) {
        System.out.println(param);
    }

    public void outputDeviceChanged(WaveLinkOutputDevice param) {
        System.out.println(param);
    }

    public void mixChanged(WaveLinkMix param) {
        System.out.println(param);
    }

    public void focusedAppChanged(WaveLinkApp param) {
        System.out.println(param);
    }
}
