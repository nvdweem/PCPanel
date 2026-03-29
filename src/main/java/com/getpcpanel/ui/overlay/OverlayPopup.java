package com.getpcpanel.ui.overlay;

import java.awt.Toolkit;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.ConditionalOnWindows;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@ConditionalOnWindows
@RequiredArgsConstructor
class OverlayPopup extends Popup {
    @Inject SaveService save;
    private Stage stage;
    private final OverlayController controller;

    public void prepareStage(Stage helperStage) {
        stage = helperStage;
        stage.setOpacity(0);
        stage.setScene(new Scene(new Pane(), 1, 1));

        controller.updateSaveValues();
    }

    @Override
    public void show() {
        show(stage);
    }

    public void determinePosition() {
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
}
