package dev.niels.wavelink;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.wavelink.impl.model.WaveLinkEffect;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;

public interface IWaveLinkClient {
    void setInputLevel(WaveLinkInputDevice device, WaveLinkControlAction action, int value);

    void setInputMute(WaveLinkInputDevice device, WaveLinkControlAction action, boolean mute);

    void setInputAudioEffect(WaveLinkInputDevice device, WaveLinkEffect effect);

    void setChannelLevel(WaveLinkChannel channel, WaveLinkMix mix, int value);

    void setChannelMute(WaveLinkChannel channel, WaveLinkMix mix, boolean mute);

    void addCurrentToChannel(WaveLinkChannel channel);

    void setChannelAudioEffect(WaveLinkChannel channel, WaveLinkEffect effect);

    void setMixLevel(WaveLinkMix mix, int value);

    void setMixMute(WaveLinkMix mix, boolean mute);

    void setMixOutputMute(WaveLinkOutputDevice outputDevice, WaveLinkMix mix, boolean mute);

    void setOutputLevel(WaveLinkOutputDevice outputDevice, int value); // How is this different from already existing device control?

    void setOutputMute(WaveLinkOutputDevice outputDevice, boolean mute); // How is this different from already existing device control?

    void setOutputDevice(WaveLinkOutputDevice outputDevice); // Does api offer toggle?
}
