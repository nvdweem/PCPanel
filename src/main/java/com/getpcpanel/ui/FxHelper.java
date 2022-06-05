package com.getpcpanel.ui;

import java.net.URL;

import org.springframework.stereotype.Service;

import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.SndCtrl;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.PCPanelMiniUI;
import com.getpcpanel.device.PCPanelProUI;
import com.getpcpanel.device.PCPanelRGBUI;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.obs.OBSListener;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.FileUtil;
import com.getpcpanel.voicemeeter.Voicemeeter;

import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;

/**
 * Factory for creating FX dialogs
 */
@Service
@RequiredArgsConstructor
public class FxHelper {
    private final SaveService saveService;
    private final DeviceScanner deviceScanner;
    private final OBSListener obsListener;
    private final OBS obs;
    private final Voicemeeter voicemeeter;
    private final FileUtil fileUtil;
    private final SndCtrl sndCtrl;

    public FXMLLoader getLoader(URL location) {
        var loader = new FXMLLoader(location);
        loader.setControllerFactory(MainFX::getBean);
        return loader;
    }

    public ProfileSettingsDialog buildProfileSettingsDialog(DeviceSave deviceSave, Profile profile) {
        return new ProfileSettingsDialog(saveService, this, deviceSave, profile);
    }

    public SettingsDialog buildSettingsDialog(Stage parentStage) {
        return new SettingsDialog(saveService, obsListener, fileUtil, this, parentStage);
    }

    public RGBLightingDialog buildRGBLightingDialog(PCPanelRGBUI device) {
        return new RGBLightingDialog(saveService, deviceScanner, this, device);
    }

    public ProLightingDialog buildProLightingDialog(PCPanelProUI device) {
        return new ProLightingDialog(saveService, deviceScanner, this, device);
    }

    public MiniLightingDialog buildMiniLightingDialog(PCPanelMiniUI device) {
        return new MiniLightingDialog(saveService, deviceScanner, this, device);
    }

    public BasicMacro buildBasicMacro(Device device, int knob, boolean hasButton, String name, String analogType) {
        return new BasicMacro(this, saveService, obs, voicemeeter, sndCtrl, device, knob, hasButton, name, analogType);
    }

    public BasicMacro buildBasicMacro(Device device, int knob) {
        return new BasicMacro(this, saveService, obs, voicemeeter, sndCtrl, device, knob);
    }

    public BasicMacro buildBasicMacro(Device device, int knob, boolean hasButton) {
        return new BasicMacro(this, saveService, obs, voicemeeter, sndCtrl, device, knob, hasButton);
    }

    public AppFinderDialog buildAppFinderDialog(Stage parentStage, boolean volumeApps) {
        return new AppFinderDialog(sndCtrl, this, parentStage, volumeApps);
    }
}
