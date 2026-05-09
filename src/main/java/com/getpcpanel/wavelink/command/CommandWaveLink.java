package com.getpcpanel.wavelink.command;

import com.getpcpanel.util.CdiHelper;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.wavelink.WaveLinkService;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public abstract sealed class CommandWaveLink extends Command permits CommandWaveLinkAddFocusToChannel, CommandWaveLinkChange, CommandWaveLinkChannelEffect, CommandWaveLinkMainOutput {
    protected WaveLinkService getWaveLinkService() {
        return CdiHelper.getBean(WaveLinkService.class);
    }
}
