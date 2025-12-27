package com.getpcpanel.ui.command;

import org.springframework.stereotype.Component;

import com.getpcpanel.MainFX;
import com.getpcpanel.discord.DiscordService;

@Component
public class DiscordEnabled extends Cmd.CmdEnabled {
    @Override
    public boolean isEnabled() {
        return MainFX.getBean(DiscordService.class).isEnabled();
    }
}
