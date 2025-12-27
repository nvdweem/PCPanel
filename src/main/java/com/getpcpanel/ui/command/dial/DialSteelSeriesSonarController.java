package com.getpcpanel.ui.command.dial;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandSteelSeriesSonarVolume;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.sonar.SonarService.SonarChannel;
import com.getpcpanel.sonar.SonarService.SonarMode;
import com.getpcpanel.sonar.SonarService.SonarTarget;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;

import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;

@Component
@Prototype
@Cmd(name = "SteelSeries Sonar", fxml = "SteelSeriesSonar", cmds = CommandSteelSeriesSonarVolume.class)
public class DialSteelSeriesSonarController extends DialCommandController<CommandSteelSeriesSonarVolume> {
    @FXML private ChoiceBox<SonarMode> sonarMode;
    @FXML private ChoiceBox<SonarTarget> sonarTarget;
    @FXML private ChoiceBox<SonarChannel> sonarChannel;

    @Override
    public void postInit(CommandContext context) {
        sonarMode.getItems().addAll(SonarMode.values());
        sonarTarget.getItems().addAll(SonarTarget.values());
        sonarChannel.getItems().addAll(SonarChannel.values());
        sonarMode.getSelectionModel().select(SonarMode.AUTO);
        sonarTarget.getSelectionModel().select(SonarTarget.MONITORING);
        sonarChannel.getSelectionModel().select(SonarChannel.MEDIA);
        onModeChanged(null);
    }

    @Override
    public void initFromCommand(CommandSteelSeriesSonarVolume cmd) {
        sonarMode.setValue(cmd.getMode());
        sonarTarget.setValue(cmd.getTarget());
        sonarChannel.setValue(cmd.getChannel());
        onModeChanged(null);
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand(DialCommandParams params) {
        return new CommandSteelSeriesSonarVolume(
                sonarMode.getValue(),
                sonarTarget.getValue(),
                sonarChannel.getValue(),
                params);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] {
                sonarMode.valueProperty(),
                sonarTarget.valueProperty(),
                sonarChannel.valueProperty()
        };
    }

    @FXML
    private void onModeChanged(ActionEvent ignored) {
        var classic = sonarMode.getValue() == SonarMode.CLASSIC;
        sonarTarget.setDisable(classic);
    }
}
