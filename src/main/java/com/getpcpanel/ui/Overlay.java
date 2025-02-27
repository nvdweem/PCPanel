package com.getpcpanel.ui;

import java.awt.Toolkit;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.ConditionalOnWindows;
import com.getpcpanel.util.Debouncer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;

@Component
@ConditionalOnWindows
@RequiredArgsConstructor
public class Overlay extends Popup {
    private final FxHelper fxHelper;
    private final SaveService save;
    private final IconService iconService;
    private final Debouncer debouncer;

    @FXML private Pane panel;
    @FXML private HBox volumePanel;
    @FXML private HBox textPanel;
    @FXML private ProgressBar volume;
    @FXML private Label volumeText;
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
        getContent().addAll(panel);
        updateSaveValues();
    }

    @EventListener(SaveService.SaveEvent.class)
    public void updateSaveValues() {
        updateStyle();
        determinePosition();
    }

    private void determinePosition() {
        var window = Toolkit.getDefaultToolkit().getScreenSize();
        var x = window.getWidth();
        var y = window.getHeight();
        var width = getWidth();
        var height = getHeight();

        switch (save.get().getOverlayPosition()) {
            case topLeft, topMiddle, topRight -> setY(save.get().getOverlayPadding());
            case middleLeft, middleMiddle, middleRight -> setY(y / 2 - height / 2);
            case bottomLeft, bottomMiddle, bottomRight -> setY(y - getHeight() - save.get().getOverlayPadding());
        }
        switch (save.get().getOverlayPosition()) {
            case topLeft, middleLeft, bottomLeft -> setX(save.get().getOverlayPadding());
            case topMiddle, middleMiddle, bottomMiddle -> setX(x / 2 - width / 2);
            case topRight, middleRight, bottomRight -> setX(x - width - save.get().getOverlayPadding());
        }
    }

    @EventListener(SaveService.SaveEvent.class)
    public void updateStyle() {
        var save = this.save.get();
        var style = "-fx-background-color: " + save.getOverlayBackgroundColor() + ";";
        if (save.getOverlayWindowCornerRounding() > 0)
            style += "-fx-background-radius: " + save.getOverlayWindowCornerRounding() + "px;";
        panel.setStyle(style);
        volumeText.setTextFill(Color.web(save.getOverlayTextColor()));
        text.setTextFill(Color.web(save.getOverlayTextColor()));
    }

    private void initAfterShow() {
        determinePosition();
        var save = this.save.get();
        if (save.getOverlayBarCornerRounding() > 0) {
            var barStyle = "-fx-background-radius: " + save.getOverlayBarCornerRounding() + "px;";
            volume.setStyle(barStyle);
            volume.lookup(".track").setStyle(barStyle + "-fx-background-color: " + save.getOverlayBarBackgroundColor() + ";");
            volume.lookup(".bar").setStyle(barStyle + "-fx-background-color: " + save.getOverlayBarColor() + ";");
        }
        volume.setPrefHeight(save.getOverlayBarHeight());
    }

    @EventListener
    public void handleControl(PCPanelControlEvent event) {
        if (event.initial()) {
            return;
        }
        showDebounced(() -> determineIconImage(event), command -> {
            if (event.vol() != null) {
                var value = save.get().isOverlayUseLog() ? event.vol().getValue(null, 0, 255) : event.vol().value();
                setVisible(volumePanel);
                volume.setProgress(value / 255f);

                if (save.get().isOverlayShowNumber()) {
                    volumeText.setText(String.valueOf(Math.round(value / 2.55f)));
                    volumeText.setVisible(true);
                    volumeText.setManaged(true);
                } else {
                    volumeText.setVisible(false);
                    volumeText.setManaged(false);
                }
                return true;
            } else {
                setVisible(textPanel);
                var firstButtonAction = StreamEx.of(Commands.cmds(command)).select(ButtonAction.class).findFirst();
                if (firstButtonAction.isPresent()) {
                    text.setText(firstButtonAction.get().getOverlayText());
                    return true;
                }
                return false;
            }
        });
    }

    private void showDebounced(Supplier<CommandAndIcon> pre, Predicate<Commands> pred) {
        if (!save.get().isOverlayEnabled()) {
            return;
        }
        Platform.runLater(() -> {
            var cai = pre.get();
            if (hasOverlay(cai.command) && pred.test(cai.command)) {
                icon.setImage(cai.icon);
                show(stage);
                initAfterShow();
            }
        });
        debouncer.debounce(this, () -> Platform.runLater(this::hide), 2, TimeUnit.SECONDS);
    }

    private boolean hasOverlay(Commands commands) {
        return Commands.hasCommands(commands) &&
                StreamEx.of(commands.getCommands()).anyMatch(command -> command instanceof DialAction da && da.hasOverlay()
                        || command instanceof ButtonAction ba && ba.hasOverlay());
    }

    private @Nonnull CommandAndIcon determineIconImage(PCPanelControlEvent event) {
        return save.getProfile(event.serialNum()).map(profile -> {
            var data = event.cmd();
            var setting = event.vol() == null ? null : profile.getKnobSettings(event.knob());
            return new CommandAndIcon(data, iconService.getImageFrom(data, setting));
        }).orElse(CommandAndIcon.DEFAULT);
    }

    @SuppressWarnings("ObjectEquality")
    private void setVisible(Node param) {
        List.of(volumePanel, textPanel).forEach(node -> node.setVisible(node == param));
    }

    private record CommandAndIcon(Commands command, Image icon) {
        static final CommandAndIcon DEFAULT = new CommandAndIcon(Commands.EMPTY, IconService.DEFAULT);
    }
}
