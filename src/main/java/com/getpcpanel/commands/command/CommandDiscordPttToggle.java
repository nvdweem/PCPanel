package com.getpcpanel.commands.command;

import com.getpcpanel.MainFX;
import com.getpcpanel.discord.DiscordService;

import lombok.ToString;

@ToString(callSuper = true)
public class CommandDiscordPttToggle extends Command implements ButtonAction {
    @Override
    public void execute() {
        MainFX.getBean(DiscordService.class).togglePttMode();
    }

    @Override
    public String buildLabel() {
        return "Discord PTT toggle";
    }
}
