package com.getpcpanel.ui;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nullable;

import org.springframework.stereotype.Service;

import com.getpcpanel.MainFX;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.PCPanelMiniUI;
import com.getpcpanel.device.PCPanelProUI;
import com.getpcpanel.device.PCPanelRGBUI;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import one.util.streamex.StreamEx;

/**
 * Factory for creating FX dialogs
 */
@Service
@RequiredArgsConstructor
public class FxHelper {
    public FXMLLoader getLoader(@Nullable URL location) {
        var loader = new FXMLLoader(location);
        loader.setControllerFactory(MainFX::getBean);
        return loader;
    }

    public <T> T open(Class<T> dialogClass, Object... initializer) {
        var loader = getLoader(getClass().getResource("/assets/%s.fxml".formatted(dialogClass.getSimpleName())));
        try {
            loader.load();
            var controller = loader.<T>getController();
            if (controller instanceof UIInitializer init) {
                init.initUI(initializer);
            }
            return controller;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ProfileSettingsDialog buildProfileSettingsDialog(DeviceSave save, Profile profile) {
        return open(ProfileSettingsDialog.class, save, profile);
    }

    public SettingsDialog buildSettingsDialog(Stage parentStage) {
        return open(SettingsDialog.class, parentStage);
    }

    public RGBLightingDialog buildRGBLightingDialog(PCPanelRGBUI device) {
        return open(RGBLightingDialog.class, device);
    }

    public ProLightingDialog buildProLightingDialog(PCPanelProUI device) {
        return open(ProLightingDialog.class, device);
    }

    public MiniLightingDialog buildMiniLightingDialog(PCPanelMiniUI device) {
        return open(MiniLightingDialog.class, device);
    }

    public BasicMacro buildBasicMacro(Device device, int knob, boolean hasButton, String name, String analogType) {
        return open(BasicMacro.class, device, knob, hasButton, name, analogType);
    }

    public BasicMacro buildBasicMacro(Device device, int knob) {
        return open(BasicMacro.class, device, knob, null, null, null);
    }

    public AppFinderDialog buildAppFinderDialog(Stage parentStage, boolean volumeApps) {
        return open(AppFinderDialog.class, parentStage, volumeApps);
    }

    public void removeTabById(TabPane tabPane, String name) {
        var tab = getTabById(tabPane, name);
        if (tab != null)
            tabPane.getTabs().remove(tab);
    }

    public @Nullable Tab getTabById(TabPane tabPane, String name) {
        return StreamEx.of(tabPane.getTabs()).findFirst(tab -> tab.getId().equals(name)).orElse(null);
    }

    public void selectTabById(TabPane tabPane, String name) {
        var tab = getTabById(tabPane, name);
        if (tab != null)
            tabPane.getSelectionModel().select(tab);
    }

    public String getSelectedTabId(TabPane tabPane) {
        var tab = tabPane.getSelectionModel().getSelectedItem();
        return (tab == null) ? "" : tab.getId();
    }
}
