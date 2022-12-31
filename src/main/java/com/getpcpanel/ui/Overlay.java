package com.getpcpanel.ui;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.ConditionalOnWindows;
import com.getpcpanel.util.Debouncer;

import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnWindows
@RequiredArgsConstructor
public class Overlay extends Popup {
    private final FxHelper fxHelper;
    private final SaveService save;
    private final IconService iconService;
    private final Debouncer debouncer;

    @FXML private Pane panel;
    @FXML private ProgressBar volume;
    @FXML private Label text;
    @FXML private ImageView icon;
    private Stage stage;

    @PostConstruct
    public void doInit() {
        Platform.runLater(() -> prepareStage(new Stage(StageStyle.UTILITY)));
    }

    public void prepareStage(Stage helperStage) {
        stage = helperStage;
        stage.setOpacity(0);
        helperStage.show();
        var loader = fxHelper.getLoader(getClass().getResource("/assets/Overlay.fxml"));
        loader.setController(this);
        HBox panel;
        try {
            panel = loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        panel.setStyle("-fx-background-color: rgba(0, 0, 0, 0);");
        getContent().addAll(panel);
        setX(10);
        setY(10);
    }

    @EventListener
    public void onKnobRotate(DeviceCommunicationHandler.KnobRotateEvent event) {
        if (event.initial()) {
            return;
        }
        showDebounced(() -> {
            var command = determineIconImage(event);
            setVisible(volume);
            volume.setProgress(event.value() / 255f);
            return command;
        });
    }

    @EventListener
    public void buttonPressEvent(DeviceCommunicationHandler.ButtonPressEvent event) {
        showDebounced(() -> {
            var command = determineIconImage(event);
            setVisible(text);

            if (command instanceof ButtonAction ba) {
                text.setText(ba.getOverlayText());
                return command;
            }
            return null;
        });
    }

    private void showDebounced(Supplier<Command> setup) {
        if (!save.get().isOverlayEnabled()) {
            return;
        }
        Platform.runLater(() -> {
            var command = setup.get();
            if (hasOverlay(command)) {
                show(stage);
            }
        });
        debouncer.debounce(this, () -> Platform.runLater(this::hide), 2, TimeUnit.SECONDS);
    }

    private boolean hasOverlay(Command command) {
        return command instanceof DialAction da && da.hasOverlay()
                || command instanceof ButtonAction ba && ba.hasOverlay();
    }

    private Command determineIconImage(DeviceCommunicationHandler.KnobRotateEvent event) {
        var save = this.save.get().getDeviceSave(event.serialNum());
        var data = save.getDialData(event.knob());
        var setting = save.getKnobSettings(event.knob());
        icon.setImage(iconService.getImageFrom(data, setting));
        return data;
    }

    private Command determineIconImage(DeviceCommunicationHandler.ButtonPressEvent event) {
        var save = this.save.get().getDeviceSave(event.serialNum());
        var data = save.getButtonData(event.button());
        icon.setImage(iconService.getImageFrom(data, null));
        return data;
    }

    @SuppressWarnings("ObjectEquality")
    private void setVisible(Control param) {
        List.of(volume, text).forEach(node -> node.setVisible(node == param));
        if (param == text) {
            panel.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5);");
        } else {
            panel.setStyle(null);
        }
    }
}
