package com.getpcpanel.wavelink.ui;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

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
import com.getpcpanel.wavelink.command.CommandWaveLinkChannelEffect;
import com.getpcpanel.wavelink.command.CommandWaveLinkMainOutput;
import com.getpcpanel.wavelink.command.WaveLinkCommandTarget;

import dev.niels.elgato.wavelink.impl.model.WaveLinkEffect;
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
@Cmd(name = "WaveLink", fxml = "WaveLink", cmds = { CommandWaveLinkChangeMute.class, CommandWaveLinkMainOutput.class, CommandWaveLinkAddFocusToChannel.class, CommandWaveLinkChannelEffect.class }, enabled = WaveLinkEnabled.class)
public class BtnWaveLinkController extends BaseWaveLinkController<CommandWaveLink> implements ButtonCommandController {
    @FXML private RadioButton type_mute;
    @FXML private RadioButton type_mainoutput;
    @FXML private RadioButton type_add_focus_to_channel;
    @FXML private RadioButton type_channel_effect;
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
            } else if (type_channel_effect.isSelected()) {
                choice2Label.setText("Effect:");
                typeChoice.setValue(WaveLinkCommandTarget.Channel);
            }
            triggerVisibility();
        });

        keepChoice2EffectsUpToDate();
    }

    private void keepChoice2EffectsUpToDate() {
        choice1.valueProperty().addListener((obs, oldV, newV) -> {
            if (!type_channel_effect.isSelected()) {
                return;
            }

            var channel = waveLinkService.getChannelFromId(newV.id());
            var effects = Objects.requireNonNullElseGet(channel.effects(), List::<WaveLinkEffect>of);
            choice2.getItems().setAll(StreamEx.of(effects)
                                              .map(effect -> new Entry(effect.id(), effect.name()))
                                              .toList());
        });
    }

    @Override
    protected void triggerVisibility() {
        var selectedToggle = type_grp.getSelectedToggle();
        if (selectedToggle == type_mute) {
            setVisible(typeLabel, true);
            setVisible(typeChoice, true);
            setVisible(mute_box, true);
            setMuteLabels(true);
            super.triggerVisibility();
        } else if (selectedToggle == type_mainoutput || selectedToggle == type_add_focus_to_channel || selectedToggle == type_channel_effect) {
            setVisible(typeLabel, false);
            setVisible(typeChoice, false);
            setVisible(choice1Label, true);
            setVisible(choice1, true);

            setMuteLabels(false);
            setVisible(choice2Label, selectedToggle == type_channel_effect);
            setVisible(choice2, selectedToggle == type_channel_effect);
            setVisible(mute_box, selectedToggle == type_channel_effect);
        }
    }

    private void setMuteLabels(boolean mute) {
        if (mute) {
            mute_mute.setText("Mute");
            mute_unmute.setText("Unmute");
            mute_toggle.setText("Toggle Mute/Unmute");
        } else {
            mute_mute.setText("Effect on");
            mute_unmute.setText("Effect off");
            mute_toggle.setText("Toggle Effect");
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
                setMuteToggle(muteCmd.getMuteType());
            }
            case CommandWaveLinkAddFocusToChannel addFocusToChannel -> {
                type_grp.selectToggle(type_add_focus_to_channel);
                typeChoice.setValue(WaveLinkCommandTarget.Channel);
                choice1.setValue(new Entry(addFocusToChannel.getChannelId(), addFocusToChannel.getChannelName()));
            }
            case CommandWaveLinkChannelEffect channelEffect -> {
                type_grp.selectToggle(type_channel_effect);
                typeChoice.setValue(WaveLinkCommandTarget.Channel);
                choice1.setValue(new Entry(channelEffect.getChannelId(), channelEffect.getChannelName()));
                choice2.setValue(new Entry(channelEffect.getEffectId(), channelEffect.getEffectName()));
                setMuteToggle(channelEffect.getToggleType());
            }
            default -> {
                log.debug("Unknown command {}", cmd);
            }
        }
        super.initFromCommand(cmd);
    }

    private void setMuteToggle(@Nullable MuteType toggleType) {
        mute_toggle_grp.selectToggle(
                switch (toggleType) {
                    case mute -> mute_mute;
                    case unmute -> mute_unmute;
                    case toggle -> mute_toggle;
                    case null -> mute_toggle;
                }
        );
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
        if (type_channel_effect.isSelected()) {
            var choice2 = getChoice2();
            if (choice2.isEmpty()) {
                return CommandNoOp.NOOP;
            }
            return new CommandWaveLinkChannelEffect(choice1.get().id(), choice1.get().name(), choice2.get().id(), choice2.get().name(), getMuteType());
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
