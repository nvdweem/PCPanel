package com.getpcpanel.elgato.wavelink.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.elgato.wavelink.WaveLinkService;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract class CommandWaveLink extends Command {
    protected WaveLinkService getWaveLinkService() {
        return MainFX.getBean(WaveLinkService.class);
    }
}
