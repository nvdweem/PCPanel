package com.getpcpanel.wavelink;

import java.util.Optional;

import javax.annotation.Nullable;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.commands.IIconHandler;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.wavelink.command.CommandWaveLink;
import com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChange;
import com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect;
import com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkImage;
import java.awt.image.BufferedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class WaveLinkIconHandler implements IIconHandler<CommandWaveLink> {
    @Inject
    WaveLinkService waveLinkService;

    @Override
    public Class<CommandWaveLink> getCommandClass() {
        return CommandWaveLink.class;
    }

    @Override
    @Nullable
    public Optional<BufferedImage> supplyImage(CommandWaveLink cmd) {
        if (cmd instanceof CommandWaveLinkMainOutput) {
            return Optional.of(outputImage());
        } else if (cmd instanceof CommandWaveLinkChannelEffect ce) {
            return channelImage(ce.getChannelId());
        } else if (cmd instanceof CommandWaveLinkChange c) {
            return fromChange(c);
        } else if (cmd instanceof CommandWaveLinkAddFocusToChannel ac) {
            return channelImage(ac.getChannelId());
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<BufferedImage> fromChange(CommandWaveLinkChange change) {
        switch (change.getCommandType()) {
            case Channel, Mix -> {
                return channelImage(change.getId1());
            }
            case Output -> {
                return Optional.of(outputImage());
            }
        }
        return Optional.empty();
    }

    private Optional<BufferedImage> channelImage(@Nullable String id) {
        return Optional.ofNullable(waveLinkService.getChannels().get(id))
                       .map(WaveLinkChannel::image)
                       .map(WaveLinkImage::getImage);
    }

    private BufferedImage outputImage() {
        return IconService.DEVICE;
    }
}
