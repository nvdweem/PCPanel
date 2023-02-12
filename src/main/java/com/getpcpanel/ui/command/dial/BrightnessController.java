package com.getpcpanel.ui.command.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandBrightness;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.CommandController;

import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
public class BrightnessController implements CommandController<CommandBrightness> {
    @Override
    public void postInit(Stage stage, Command cmd) {
    }

    @Override
    public void initFromCommand(CommandBrightness cmd) {
    }

    @Override
    public Command buildCommand() {
        return new CommandBrightness();
    }
}
