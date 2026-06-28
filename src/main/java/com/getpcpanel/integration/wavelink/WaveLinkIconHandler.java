package com.getpcpanel.integration.wavelink;

import java.awt.image.BufferedImage;
import java.util.Optional;

import javax.annotation.Nullable;

import com.getpcpanel.commands.IIconHandler;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.integration.wavelink.command.CommandWaveLink;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkAddFocusToChannel;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkChange;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkChannelEffect;
import com.getpcpanel.integration.wavelink.command.CommandWaveLinkMainOutput;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkImage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
class WaveLinkIconHandler implements IIconHandler<CommandWaveLink> {
    @Inject
    WaveLinkService waveLinkService;
    @Inject
    IconService iconService;

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
            return channelImage(ac.getId());
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
        return iconService.DEVICE;
    }
}
