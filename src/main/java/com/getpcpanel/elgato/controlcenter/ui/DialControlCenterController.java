package com.getpcpanel.elgato.controlcenter.ui;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.elgato.controlcenter.ControlCenterService;
import com.getpcpanel.elgato.controlcenter.command.ControlCenterSetLightValue;
import com.getpcpanel.elgato.controlcenter.command.ControlCenterSetLightValue.LightValueType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@Cmd(name = "ControlCenter", fxml = "ControlCenter", cmds = ControlCenterSetLightValue.class, enabled = ControlCenterEnabled.class)
public class DialControlCenterController extends BaseControlCenterController<ControlCenterSetLightValue> implements DialCommandController {
    @FXML private ChoiceBox<Entry> typeChoice;
    @FXML private CheckBox minIsOff;
    @FXML private CheckBox changeIsOn;

    public DialControlCenterController(ControlCenterService controlCenterService) {
        super(controlCenterService);
    }

    @Override
    public void postInit(CommandContext context) {
        super.postInit(context);

        StreamEx.of(LightValueType.values())
                .map(type -> new Entry(type.name(), StringUtils.capitalize(type.name())))
                .into(typeChoice.getItems());
    }

    @Override
    public void initFromCommand(ControlCenterSetLightValue cmd) {
        super.initFromCommand(cmd);
        selectId(typeChoice, Optional.ofNullable(cmd.getType()).map(Enum::name).orElse(""));
        minIsOff.setSelected(cmd.isMinIsOff());
        changeIsOn.setSelected(cmd.isChangeIsOn());
    }

    @Override
    public Command buildCommand(DialCommandParams params) {
        var type = typeChoice.getValue() != null ? LightValueType.valueOf(typeChoice.getValue().id()) : null;
        return new ControlCenterSetLightValue(getSelectedDeviceId(), type, minIsOff.isSelected(), changeIsOn.isSelected(), params);
    }
}
