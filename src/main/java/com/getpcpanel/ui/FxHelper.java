package com.getpcpanel.ui;

import static com.getpcpanel.MainFX.getBean;

import java.net.URL;

import org.springframework.stereotype.Service;

import com.getpcpanel.Json;
import com.getpcpanel.MainFX;
import com.getpcpanel.cpp.ISndCtrl;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.PCPanelMiniUI;
import com.getpcpanel.device.PCPanelProUI;
import com.getpcpanel.device.PCPanelRGBUI;
import com.getpcpanel.hid.DeviceScanner;
import com.getpcpanel.iconextract.IIconService;
import com.getpcpanel.obs.OBS;
import com.getpcpanel.obs.OBSListener;
import com.getpcpanel.profile.DeviceSave;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.FileUtil;
import com.getpcpanel.util.IPlatformCommand;
import com.getpcpanel.util.ShortcutHook;
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
    public FXMLLoader getLoader(URL location) {
        var loader = new FXMLLoader(location);
        loader.setControllerFactory(MainFX::getBean);
        return loader;
    }

    public ProfileSettingsDialog buildProfileSettingsDialog(DeviceSave deviceSave, Profile profile) {
        return new ProfileSettingsDialog(getBean(SaveService.class), this, getBean(ShortcutHook.class), deviceSave, profile);
    }

    public SettingsDialog buildSettingsDialog(Stage parentStage) {
        return new SettingsDialog(getBean(SaveService.class), getBean(Json.class), getBean(OBSListener.class), getBean(FileUtil.class), this, getBean(IPlatformCommand.class), parentStage);
    }

    public RGBLightingDialog buildRGBLightingDialog(PCPanelRGBUI device) {
        return new RGBLightingDialog(getBean(SaveService.class), getBean(DeviceScanner.class), this, device);
    }

    public ProLightingDialog buildProLightingDialog(PCPanelProUI device) {
        return new ProLightingDialog(getBean(SaveService.class), getBean(DeviceScanner.class), this, device);
    }

    public MiniLightingDialog buildMiniLightingDialog(PCPanelMiniUI device) {
        return new MiniLightingDialog(getBean(SaveService.class), getBean(DeviceScanner.class), this, device);
    }

    public BasicMacro buildBasicMacro(Device device, int knob, boolean hasButton, String name, String analogType) {
        return new BasicMacro(this, getBean(SaveService.class), getBean(OBS.class), getBean(Voicemeeter.class), getBean(ISndCtrl.class), device, knob, hasButton, name, analogType);
    }

    public BasicMacro buildBasicMacro(Device device, int knob) {
        return new BasicMacro(this, getBean(SaveService.class), getBean(OBS.class), getBean(Voicemeeter.class), getBean(ISndCtrl.class), device, knob);
    }

    public AppFinderDialog buildAppFinderDialog(Stage parentStage, boolean volumeApps) {
        return new AppFinderDialog(getBean(ISndCtrl.class), this, getBean(IIconService.class), parentStage, volumeApps);
    }
}
