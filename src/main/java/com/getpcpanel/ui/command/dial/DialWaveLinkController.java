package com.getpcpanel.ui.command.dial;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction.DialCommandParams;
import com.getpcpanel.commands.command.wavelink.CommandWaveLinkLevel;
import com.getpcpanel.commands.command.wavelink.CommandWaveLinkLevel.WaveLinkCommand;
import com.getpcpanel.spring.Prototype;
import com.getpcpanel.ui.command.Cmd;
import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.DialCommandController;
import com.getpcpanel.wavelink.WaveLinkService;

import dev.niels.wavelink.impl.model.WaveLinkChannel;
import dev.niels.wavelink.impl.model.WaveLinkInputDevice;
import dev.niels.wavelink.impl.model.WaveLinkMix;
import dev.niels.wavelink.impl.model.WaveLinkOutputDevice;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import javafx.beans.Observable;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

@Log4j2
@Component
@Prototype
@RequiredArgsConstructor
/*, enabled = VoiceMeeterEnabled.class*/
@Cmd(name = "WaveLink", fxml = "WaveLink", cmds = CommandWaveLinkLevel.class)
public class DialWaveLinkController extends DialCommandController<CommandWaveLinkLevel> {
    private final WaveLinkService waveLinkService;
    @FXML private ChoiceBox<WaveLinkCommand> typeChoice;
    @FXML private Label choice1Label;
    @FXML private ChoiceBox<Entry> choice1;
    @FXML private Label choice2Label;
    @FXML private ChoiceBox<Entry> choice2;

    @Override
    public void postInit(CommandContext context) {
        if (!waveLinkService.isConnected()) {
            return;
        }

        typeChoice.getItems().setAll(WaveLinkCommand.values());
        typeChoice.valueProperty().addListener((obs, oldV, newV) -> {
            choice1Label.setVisible(true);
            choice1.setVisible(true);
            choice2Label.setVisible(false);
            choice2.setVisible(false);
            choice1.getItems().clear();
            choice2.getItems().clear();
            switch (newV) {
                case Input -> {
                    choice1Label.setText("Input");
                    EntryStream.of(waveLinkService.getInputDevices())
                               .mapValues(WaveLinkInputDevice::name)
                               .mapKeyValue(Entry::new)
                               .into(choice1.getItems());
                }
                case Channel -> {
                    choice1Label.setText("Channel");
                    EntryStream.of(waveLinkService.getChannels())
                               .mapValues(WaveLinkChannel::name)
                               .mapKeyValue(Entry::new)
                               .into(choice1.getItems());
                }
                case Mix -> {
                    choice1Label.setText("Channel");
                    choice2Label.setVisible(true);
                    choice2.setVisible(true);

                    EntryStream.of(waveLinkService.getChannels())
                               .mapValues(WaveLinkChannel::name)
                               .mapKeyValue(Entry::new)
                               .into(choice1.getItems());
                    EntryStream.of(waveLinkService.getMixes())
                               .mapValues(WaveLinkMix::name)
                               .mapKeyValue(Entry::new)
                               .into(choice2.getItems());
                }
                case Output -> {
                    choice1Label.setText("Output device");
                    EntryStream.of(waveLinkService.getOutputDevices())
                               .mapValues(WaveLinkOutputDevice::name)
                               .mapKeyValue(Entry::new)
                               .into(choice1.getItems());
                }
                default -> {
                    choice1Label.setVisible(false);
                    choice1.setVisible(false);
                }
            }
        });
    }

    @Override
    public void initFromCommand(CommandWaveLinkLevel cmd) {
        typeChoice.setValue(cmd.getCommandType());
        selectId(choice1, cmd.getId1());
        selectId(choice2, cmd.getId2());

        super.initFromCommand(cmd);
    }

    private void selectId(ChoiceBox<Entry> choice, @Nullable String cmd) {
        EntryStream.of(choice.getItems()).filterValues(v -> Objects.equals(cmd, v.id()))
                   .keys().findFirst()
                   .ifPresent(integer -> choice.getSelectionModel().select(integer));
    }

    @Override
    public Command buildCommand(DialCommandParams params) {
        var command = Optional.ofNullable(typeChoice.getValue()).orElse(WaveLinkCommand.Channel);
        var choice1Id = Optional.ofNullable(choice1.getValue()).map(Entry::id).orElse(null);
        var choice2Id = Optional.ofNullable(choice2.getValue()).map(Entry::id).orElse(null);
        return new CommandWaveLinkLevel(command, choice1Id, choice2Id);
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[0];
    }

    record Entry(String id, String name) {
        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }
}
