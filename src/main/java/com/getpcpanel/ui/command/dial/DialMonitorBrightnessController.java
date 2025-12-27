package com.getpcpanel.ui.command.dial;

import static com.getpcpanel.spring.OsHelper.WINDOWS;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandMonitorBrightness;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.monitor.MonitorBrightnessService;
import com.getpcpanel.monitor.MonitorInfo;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
@Cmd(name = "Monitor Brightness", fxml = "MonitorBrightness", cmds = CommandMonitorBrightness.class, os = WINDOWS)
public class DialMonitorBrightnessController extends DialCommandController<CommandMonitorBrightness> {
    private final MonitorBrightnessService monitorBrightnessService;
    private List<MonitorInfo> monitors;
    @FXML private ChoiceBox<MonitorInfo> monitorChoice;

    @Override
    public void postInit(CommandContext context) {
        monitors = monitorBrightnessService.getMonitors();
        monitorChoice.getItems().add(new MonitorInfo("", "All Monitors"));
        monitorChoice.getItems().addAll(monitors);
        monitorChoice.getSelectionModel().selectFirst();
    }

    @Override
    public void initFromCommand(CommandMonitorBrightness cmd) {
        if (StringUtils.isNotBlank(cmd.getMonitorId())) {
            var found = StreamEx.of(monitors).findFirst(m -> m.id().equals(cmd.getMonitorId())).orElse(null);
            if (found != null) {
                monitorChoice.setValue(found);
            }
        } else {
            monitorChoice.getSelectionModel().selectFirst();
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand(DialCommandParams params) {
        var selected = monitorChoice.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return new CommandMonitorBrightness("", "", params);
        }
        return new CommandMonitorBrightness(selected.id(), selected.name(), params);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] { monitorChoice.valueProperty() };
    }
}
