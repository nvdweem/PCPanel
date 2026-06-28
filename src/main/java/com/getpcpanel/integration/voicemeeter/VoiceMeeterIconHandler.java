package com.getpcpanel.integration.voicemeeter;

import java.awt.image.BufferedImage;
import java.util.Optional;

import com.getpcpanel.commands.IIconHandler;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.integration.voicemeeter.command.CommandVoiceMeeter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Supplies the VoiceMeeter icon for any VoiceMeeter command, contributed via the {@link IIconHandler} SPI. */
@ApplicationScoped
public class VoiceMeeterIconHandler implements IIconHandler<CommandVoiceMeeter> {
    @Inject
    IconService iconService;

    @Override
    public Class<CommandVoiceMeeter> getCommandClass() {
        return CommandVoiceMeeter.class;
    }

    @Override
    public Optional<BufferedImage> supplyImage(CommandVoiceMeeter cmd) {
        return Optional.ofNullable(iconService.VOICEMEETER);
    }
}
