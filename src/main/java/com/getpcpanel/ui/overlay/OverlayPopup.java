package com.getpcpanel.ui.overlay;

import java.awt.Toolkit;

import com.getpcpanel.profile.SaveService;
import com.getpcpanel.spring.ConditionalOnWindows;

import jakarta.enterprise.context.ApplicationScoped;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Popup;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@ConditionalOnWindows
@RequiredArgsConstructor
class OverlayPopup extends Popup {
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

        switch (getSave().get().getOverlayPosition()) {
            case topLeft, topMiddle, topRight -> setY(getSave().get().getOverlayPadding());
            case middleLeft, middleMiddle, middleRight -> setY(y / 2 - height / 2);
            case bottomLeft, bottomMiddle, bottomRight -> setY(y - getHeight() - getSave().get().getOverlayPadding());
        }
        switch (getSave().get().getOverlayPosition()) {
            case topLeft, middleLeft, bottomLeft -> setX(getSave().get().getOverlayPadding());
            case topMiddle, middleMiddle, bottomMiddle -> setX(x / 2 - width / 2);
            case topRight, middleRight, bottomRight -> setX(x - width - getSave().get().getOverlayPadding());
        }
    }

    private SaveService getSave() {
        return controller.save;
    }
}
