package com.getpcpanel.ui.command.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.commands.command.wavelink.CommandWaveLink;
import com.getpcpanel.commands.command.wavelink.CommandWaveLinkLevel;
import com.getpcpanel.commands.command.wavelink.CommandWaveLinkLevel.WaveLinkCommand;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;

import javafx.beans.Observable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
/*, enabled = VoiceMeeterEnabled.class*/
@Cmd(name = "WaveLink", fxml = "WaveLink", cmds = CommandWaveLinkLevel.class)
public class DialWaveLinkController extends DialCommandController<CommandWaveLink> {

    @Override
    public void postInit(CommandContext context) {
    }

    @Override
    public void initFromCommand(CommandWaveLink cmd) {
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand(DialCommandParams params) {
        return new CommandWaveLinkLevel(WaveLinkCommand.Mix, "", DialCommandParams.DEFAULT);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[0];
    }
}
