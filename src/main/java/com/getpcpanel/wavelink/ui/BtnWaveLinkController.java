package com.getpcpanel.wavelink.ui;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.CommandNoOp;
import com.getpcpanel.cpp.MuteType;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.ButtonCommandController;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.wavelink.WaveLinkService;
import com.getpcpanel.wavelink.command.CommandWaveLink;
import com.getpcpanel.wavelink.command.CommandWaveLinkAddFocusToChannel;
import com.getpcpanel.wavelink.command.CommandWaveLinkChangeMute;
import com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput;
import com.getpcpanel.wavelink.command.WaveLinkCommandTarget;

import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Component
@Prototype
@Cmd(name = "WaveLink", fxml = "WaveLink", cmds = { CommandWaveLinkChangeMute.class, CommandWaveLinkMainOutput.class, CommandWaveLinkAddFocusToChannel.class }, enabled = WaveLinkEnabled.class)
public class BtnWaveLinkController extends BaseWaveLinkController<CommandWaveLink> implements ButtonCommandController {
    @FXML private RadioButton type_mute;
    @FXML private RadioButton type_mainoutput;
    @FXML private RadioButton type_add_focus_to_channel;
    @FXML private ToggleGroup type_grp;

    @FXML private VBox mute_box;
    @FXML private RadioButton mute_toggle;
    @FXML private RadioButton mute_mute;
    @FXML private RadioButton mute_unmute;
    @FXML private ToggleGroup mute_toggle_grp;

    public BtnWaveLinkController(WaveLinkService waveLinkService) {
        super(waveLinkService);
    }

    @Override
    public void postInit(CommandContext context) {
        super.postInit(context);

        type_grp.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (type_mainoutput.isSelected()) {
                typeChoice.setValue(WaveLinkCommandTarget.Output);
            } else if (type_add_focus_to_channel.isSelected()) {
                typeChoice.setValue(WaveLinkCommandTarget.Channel);
            }
            triggerVisibility();
        });
    }

    @Override
    protected void triggerVisibility() {
        var selectedToggle = type_grp.getSelectedToggle();
        if (selectedToggle == type_mute) {
            typeLabel.setVisible(true);
            typeChoice.setVisible(true);
            mute_box.setVisible(true);
            super.triggerVisibility();
        } else if (selectedToggle == type_mainoutput || selectedToggle == type_add_focus_to_channel) {
            typeLabel.setVisible(false);
            typeChoice.setVisible(false);
            choice1Label.setVisible(true);
            choice1.setVisible(true);
            choice2Label.setVisible(false);
            choice2.setVisible(false);
            mute_box.setVisible(false);
        }
    }

    @Override
    public void initFromCommand(CommandWaveLink cmd) {
        switch (cmd) {
            case CommandWaveLinkMainOutput mainOutputCmd -> {
                type_grp.selectToggle(type_mainoutput);
                typeChoice.setValue(WaveLinkCommandTarget.Output);
                choice1.setValue(new Entry(mainOutputCmd.getId(), mainOutputCmd.getName()));
            }
            case CommandWaveLinkChangeMute muteCmd -> {
                type_grp.selectToggle(type_mute);
                mute_toggle_grp.selectToggle(
                        switch (muteCmd.getMuteType()) {
                            case mute -> mute_mute;
                            case unmute -> mute_unmute;
                            case toggle -> mute_toggle;
                        }
                );
            }
            case CommandWaveLinkAddFocusToChannel addFocusToChannel -> {
                type_grp.selectToggle(type_add_focus_to_channel);
                typeChoice.setValue(WaveLinkCommandTarget.Channel);
                choice1.setValue(new Entry(addFocusToChannel.getChannelId(), addFocusToChannel.getChannelName()));
            }
            default -> {
                log.debug("Unknown command {}", cmd);
            }
        }
        super.initFromCommand(cmd);
    }

    @Override
    public Command buildCommand() {
        var base = buildArgBase();
        var choice1 = getChoice1();
        if (choice1.isEmpty()) {
            return CommandNoOp.NOOP;
        }

        if (type_mute.isSelected()) {
            return new CommandWaveLinkChangeMute(base.target(), base.id1(), base.id2(), getMuteType());
        }
        if (type_mainoutput.isSelected()) {
            return new CommandWaveLinkMainOutput(choice1.get().id(), choice1.get().name());
        }
        if (type_add_focus_to_channel.isSelected()) {
            return new CommandWaveLinkAddFocusToChannel(choice1.get().id(), choice1.get().name());
        }
        return CommandNoOp.NOOP;
    }

    private MuteType getMuteType() {
        if (mute_mute.isSelected())
            return MuteType.mute;
        if (mute_unmute.isSelected())
            return MuteType.unmute;
        return MuteType.toggle;
    }

    @Override
    protected Observable[] determineDependencies() {
        return StreamEx.of(super.determineDependencies())
                       .append(mute_toggle_grp.selectedToggleProperty())
                       .toArray(Observable[]::new);
    }
}
