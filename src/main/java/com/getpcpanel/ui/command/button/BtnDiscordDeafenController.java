package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandDiscordDeafen;
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
@Cmd(name = "Discord Deafen", fxml = "DiscordDeafen", cmds = CommandDiscordDeafen.class, enabled = DiscordEnabled.class)
public class BtnDiscordDeafenController extends ButtonCommandController<CommandDiscordDeafen> {
    @FXML private RadioButton discordDeafen;
    @FXML private RadioButton discordUndeafen;
    @FXML private RadioButton discordDeafenToggle;
    @FXML private ToggleGroup discordDeafenGroup;

    @Override
    public void postInit(CommandContext context) {
        discordDeafenToggle.setSelected(true);
    }

    @Override
    public void initFromCommand(CommandDiscordDeafen cmd) {
        switch (cmd.getMuteType()) {
            case mute -> discordDeafen.setSelected(true);
            case unmute -> discordUndeafen.setSelected(true);
            case toggle -> discordDeafenToggle.setSelected(true);
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        var type = discordDeafen.isSelected() ? DiscordMuteType.mute
                : discordUndeafen.isSelected() ? DiscordMuteType.unmute
                : DiscordMuteType.toggle;
        return new CommandDiscordDeafen(type);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { discordDeafen.selectedProperty(), discordUndeafen.selectedProperty(), discordDeafenToggle.selectedProperty() };
    }
}
