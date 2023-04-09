package com.getpcpanel.ui;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.stereotype.Service;

import com.getpcpanel.MainFX;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.PCPanelMiniUI;
import com.getpcpanel.device.PCPanelProUI;
import com.getpcpanel.device.PCPanelRGBUI;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.ui.AppFinderDialog.AppFinderParams;
import com.getpcpanel.ui.BasicMacro.MacroArgs;
import com.getpcpanel.ui.ProfileSettingsDialog.ProfileSettingsArgs;
import com.getpcpanel.ui.UIInitializer.SingleParamInitializer;

import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

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

    public <P, T extends UIInitializer<P>> @Nonnull T open(@Nonnull Class<T> dialogClass, @Nullable P params) {
        var loader = getLoader(getClass().getResource("/assets/%s.fxml".formatted(dialogClass.getSimpleName())));
        try {
            loader.load();
            var controller = loader.<T>getController();
            if (params != null) {
                controller.initUI(params);
            }
            return controller;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ProfileSettingsDialog buildProfileSettingsDialog(DeviceSave save, Profile profile) {
        return open(ProfileSettingsDialog.class, new ProfileSettingsArgs(save, profile));
    }

    public SettingsDialog buildSettingsDialog(Stage parentStage) {
        return open(SettingsDialog.class, new SingleParamInitializer<>(parentStage));
    }

    public RGBLightingDialog buildRGBLightingDialog(PCPanelRGBUI device) {
        return open(RGBLightingDialog.class, new SingleParamInitializer<>(device));
    }

    public ProLightingDialog buildProLightingDialog(PCPanelProUI device) {
        return open(ProLightingDialog.class, new SingleParamInitializer<>(device));
    }

    public MiniLightingDialog buildMiniLightingDialog(PCPanelMiniUI device) {
        return open(MiniLightingDialog.class, new SingleParamInitializer<>(device));
    }

    public BasicMacro buildBasicMacro(Device device, int knob, boolean hasButton, String name, String analogType) {
        return open(BasicMacro.class, new MacroArgs(device, knob, hasButton, name, analogType));
    }

    public BasicMacro buildBasicMacro(Device device, int knob) {
        return open(BasicMacro.class, new MacroArgs(device, knob, true, null, null));
    }

    public AppFinderDialog buildAppFinderDialog(@Nullable Stage parentStage, boolean volumeApps) {
        return open(AppFinderDialog.class, new AppFinderParams(parentStage, volumeApps));
    }
}
