package com.getpcpanel.wavelink.ui;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.DialCommandController;
import com.getpcpanel.wavelink.WaveLinkService;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeLevel;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@Cmd(name = "WaveLink", fxml = "WaveLink", cmds = CommandWaveLinkChangeLevel.class, enabled = WaveLinkEnabled.class)
public class DialWaveLinkController extends BaseWaveLinkController<CommandWaveLinkChangeLevel> implements DialCommandController {
    public DialWaveLinkController(WaveLinkService waveLinkService) {
        super(waveLinkService);
    }

    @Override
    public Command buildCommand(DialCommandParams params) {
        var base = buildArgBase();
        return new CommandWaveLinkChangeLevel(base.target(), base.id1(), base.id2(), params);
    }
}
