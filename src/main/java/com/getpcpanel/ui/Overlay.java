package com.getpcpanel.ui;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.IconService;
import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Debouncer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class Overlay extends Popup {
    private final FxHelper fxHelper;
    private final SaveService save;
    private final IconService iconService;
    private final Debouncer debouncer;

    @FXML private ProgressBar volume;
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
        if (event.initial() || !save.get().isOverlayEnabled()) {
            return;
        }
        Platform.runLater(() -> {
            determineIconImage(event);
            volume.setProgress(event.value() / 255f);
            show(stage);
        });

        debouncer.debounce(this, () -> Platform.runLater(this::hide), 2, TimeUnit.SECONDS);
    }

    private void determineIconImage(DeviceCommunicationHandler.KnobRotateEvent event) {
        var save = this.save.get().getDeviceSave(event.serialNum());
        var data = save.getDialData(event.knob());
        var setting = save.getKnobSettings(event.knob());
        icon.setImage(iconService.getImageFrom(data, setting == null ? null : setting.getOverlayIcon()));
    }
}
