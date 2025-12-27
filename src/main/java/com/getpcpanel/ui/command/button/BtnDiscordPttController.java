package com.getpcpanel.ui.command.button;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandDiscordPttToggle;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DiscordEnabled;

import javafx.beans.Observable;
import lombok.RequiredArgsConstructor;

@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Discord PTT Toggle", fxml = "DiscordPtt", cmds = CommandDiscordPttToggle.class, enabled = DiscordEnabled.class)
public class BtnDiscordPttController extends ButtonCommandController<CommandDiscordPttToggle> {
    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandDiscordPttToggle cmd) {
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        return new CommandDiscordPttToggle();
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[0];
    }
}
