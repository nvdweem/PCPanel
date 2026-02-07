package com.getpcpanel.wavelink.ui;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.getpcpanel.ui.command.CommandContext;
import com.getpcpanel.ui.command.CommandController;
import com.getpcpanel.wavelink.WaveLinkService;
import com.getpcpanel.wavelink.command.CommandWaveLink;
import com.getpcpanel.wavelink.command.CommandWaveLinkChange;
import com.getpcpanel.wavelink.command.WaveLinkCommandTarget;

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
@RequiredArgsConstructor
class BaseWaveLinkController<T extends CommandWaveLink> extends CommandController<T> {
    protected final WaveLinkService waveLinkService;
    @FXML protected Label choice1Label;
    @FXML protected Label choice2Label;
    @FXML protected Label typeLabel;
    @FXML protected ChoiceBox<WaveLinkCommandTarget> typeChoice;
    @FXML protected ChoiceBox<Entry> choice1;
    @FXML protected ChoiceBox<Entry> choice2;

    @Override
    public void postInit(CommandContext context) {
        if (!waveLinkService.isConnected()) {
            return;
        }

        typeChoice.getItems().setAll(WaveLinkCommandTarget.values());
        typeChoice.valueProperty().addListener((obs, oldV, newV) -> {
            triggerVisibility();

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
            }
        });
    }

    protected void triggerVisibility() {
        setVisible(choice1Label, true);
        setVisible(choice1, true);
        setVisible(choice2Label, false);
        setVisible(choice2, false);

        switch (typeChoice.getValue()) {
            case Output -> {
                setVisible(choice2Label, true);
                setVisible(choice2, true);
            }
            default -> {
                setVisible(choice1Label, false);
                setVisible(choice1, false);
            }
        }

    }

    @Override
    public void initFromCommand(T cmd) {
        if (cmd instanceof CommandWaveLinkChange changeCmd) {
            typeChoice.setValue(changeCmd.getCommandType());
            selectId(choice1, changeCmd.getId1());
            selectId(choice2, changeCmd.getId2());
        }

        super.initFromCommand(cmd);
    }

    private void selectId(ChoiceBox<Entry> choice, @Nullable String cmd) {
        EntryStream.of(choice.getItems()).filterValues(v -> Objects.equals(cmd, v.id()))
                   .keys().findFirst()
                   .ifPresent(integer -> choice.getSelectionModel().select(integer));
    }

    @Override
    protected Observable[] determineDependencies() {
        return new Observable[] {
                typeChoice.valueProperty(),
                choice1.valueProperty(),
                choice2.valueProperty()
        };
    }

    record Entry(String id, String name) {
        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    protected ArgBase buildArgBase() {
        var command = Optional.ofNullable(typeChoice.getValue()).orElse(WaveLinkCommandTarget.Channel);
        var choice1Id = Optional.ofNullable(choice1.getValue()).map(Entry::id).orElse(null);
        var choice2Id = Optional.ofNullable(choice2.getValue()).map(Entry::id).orElse(null);
        return new ArgBase(command, choice1Id, choice2Id);
    }

    public Optional<Entry> getChoice1() {
        return Optional.ofNullable(choice1.getValue());
    }

    public Optional<Entry> getChoice2() {
        return Optional.ofNullable(choice2.getValue());
    }

    protected record ArgBase(@Nonnull WaveLinkCommandTarget target, @Nullable String id1, @Nullable String id2) {
    }
}
