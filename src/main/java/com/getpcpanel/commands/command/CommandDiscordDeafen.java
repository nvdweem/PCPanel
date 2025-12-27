package com.getpcpanel.commands.command;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.getpcpanel.MainFX;
import com.getpcpanel.discord.DiscordMuteType;
import com.getpcpanel.discord.DiscordService;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class CommandDiscordDeafen extends Command implements ButtonAction {
    private final DiscordMuteType muteType;

    @JsonCreator
    public CommandDiscordDeafen(@JsonProperty("muteType") DiscordMuteType muteType) {
        this.muteType = muteType == null ? DiscordMuteType.toggle : muteType;
    }

    @Override
    public void execute() {
        var discord = MainFX.getBean(DiscordService.class);
        switch (muteType) {
            case mute -> discord.setDeafen(true);
            case unmute -> discord.setDeafen(false);
            case toggle -> discord.toggleDeafen();
        }
    }

    @Override
    public String buildLabel() {
        return "Discord deafen " + muteType.name();
    }
}
