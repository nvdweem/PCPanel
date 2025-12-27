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
public class CommandDiscordMute extends Command implements ButtonAction {
    private final DiscordMuteType muteType;

    @JsonCreator
    public CommandDiscordMute(@JsonProperty("muteType") DiscordMuteType muteType) {
        this.muteType = muteType == null ? DiscordMuteType.toggle : muteType;
    }

    @Override
    public void execute() {
        var discord = MainFX.getBean(DiscordService.class);
        switch (muteType) {
            case mute -> discord.setMute(true);
            case unmute -> discord.setMute(false);
            case toggle -> discord.toggleMute();
        }
    }

    @Override
    public String buildLabel() {
        return "Discord " + muteType.name();
    }
}
