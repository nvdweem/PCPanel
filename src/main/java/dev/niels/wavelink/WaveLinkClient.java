package dev.niels.wavelink;

import java.util.List;

import javax.annotation.Nullable;

import dev.niels.wavelink.impl.WaveLinkClientImpl;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkControlAction;
import dev.niels.wavelink.impl.model.WaveLinkEffect;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutput;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import dev.niels.wavelink.impl.rpc.WaveLinkSetChannelCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetMixCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetOutputDeviceCommand;
import dev.niels.wavelink.impl.rpc.WaveLinkSetOutputDeviceCommand.WaveLinkSetOutputDeviceParams;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class WaveLinkClient extends WaveLinkClientImpl implements IWaveLinkClient {
    @Override
    public void setInput(WaveLinkInputDevice device, WaveLinkControlAction action, Double value, Boolean mute) {
        log.warn("setInputLevel not implemented yet");
    }

    @Override
    public void setInputAudioEffect(WaveLinkInputDevice device, WaveLinkEffect effect) {
        log.warn("setInputAudioEffect not implemented yet");
    }

    @Override
    public void setChannel(WaveLinkChannel channel, @Nullable WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute) {
        var newChannel = channel.blank();
        if (mix == null) {
            newChannel = newChannel.withLevel(value).withIsMuted(mute);
        } else {
            newChannel = newChannel.withMixes(List.of(mix.withLevel(value).withIsMuted(mute)));
        }
        send(new WaveLinkSetChannelCommand().setParams(newChannel));
    }

    @Override
    public void addCurrentToChannel(WaveLinkChannel channel) {
        log.warn("addCurrentToChannel not implemented yet");
    }

    @Override
    public void setChannelAudioEffect(WaveLinkChannel channel, WaveLinkEffect effect) {
        send(new WaveLinkSetChannelCommand().setParams(
                channel.blank()
                       .withEffects(List.of(effect.blank().withIsEnabled(effect.isEnabled())))
        ));
    }

    @Override
    public void setMix(WaveLinkMix mix, @Nullable Double value, @Nullable Boolean mute) {
        send(new WaveLinkSetMixCommand().setParams(mix.blank().withLevel(value).withIsMuted(mute)));
    }

    @Override
    public void setMixOutput(WaveLinkOutputDevice outputDevice, WaveLinkOutput output) {
        send(new WaveLinkSetOutputDeviceCommand().setParams(new WaveLinkSetOutputDeviceParams(
                outputDevice.blank().withOutputs(List.of(output))
        )));
    }

    @Override
    public void setOutput(WaveLinkOutputDevice outputDevice, @Nullable Double value, @Nullable Boolean mute) {
        if (outputDevice.outputs() == null)
            return;

        send(new WaveLinkSetOutputDeviceCommand().setParams(new WaveLinkSetOutputDeviceParams(
                outputDevice.blankWithOutputs()
                            .withOutputs(o -> o.blank().withLevel(value).withIsMuted(mute))
        )));
    }
}
