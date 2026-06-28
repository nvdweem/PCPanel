package com.getpcpanel.obs;

import java.awt.image.BufferedImage;
import java.util.Optional;

import com.getpcpanel.commands.IIconHandler;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.obs.command.CommandObs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Supplies the OBS icon for any OBS command, contributed via the {@link IIconHandler} SPI. */
@ApplicationScoped
public class ObsIconHandler implements IIconHandler<CommandObs> {
    @Inject
    IconService iconService;

    @Override
    public Class<CommandObs> getCommandClass() {
        return CommandObs.class;
    }

    @Override
    public Optional<BufferedImage> supplyImage(CommandObs cmd) {
        return Optional.ofNullable(iconService.OBS);
    }
}
