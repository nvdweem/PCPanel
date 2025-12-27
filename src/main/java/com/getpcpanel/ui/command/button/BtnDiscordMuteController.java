package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandDiscordMute;
import com.getpcpanel.discord.DiscordMuteType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DiscordEnabled;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import lombok.RequiredArgsConstructor;

@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Discord Mute", fxml = "DiscordMute", cmds = CommandDiscordMute.class, enabled = DiscordEnabled.class)
public class BtnDiscordMuteController extends ButtonCommandController<CommandDiscordMute> {
    @FXML private RadioButton discordMute;
    @FXML private RadioButton discordUnmute;
    @FXML private RadioButton discordToggle;
    @FXML private ToggleGroup discordMuteGroup;

    @Override
    public void postInit(CommandContext context) {
        discordToggle.setSelected(true);
    }

    @Override
    public void initFromCommand(CommandDiscordMute cmd) {
        switch (cmd.getMuteType()) {
            case mute -> discordMute.setSelected(true);
            case unmute -> discordUnmute.setSelected(true);
            case toggle -> discordToggle.setSelected(true);
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        var type = discordMute.isSelected() ? DiscordMuteType.mute
                : discordUnmute.isSelected() ? DiscordMuteType.unmute
                : DiscordMuteType.toggle;
        return new CommandDiscordMute(type);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { discordMute.selectedProperty(), discordUnmute.selectedProperty(), discordToggle.selectedProperty() };
    }
}
