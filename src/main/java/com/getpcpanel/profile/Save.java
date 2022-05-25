package com.getpcpanel.profile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;

import com.getpcpanel.Json;
import com.getpcpanel.Main;
import com.getpcpanel.device.DeviceType;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class Save {
    private static Save save = new Save();
    private static final File SAVE_FILE = new File("save.json");
    private final Map<String, DeviceSave> devices = new ConcurrentHashMap<>();
    private volatile boolean obsEnabled;
    private volatile String obsAddress = "localhost";
    private volatile String obsPort = "4444";
    private volatile String obsPassword;
    private volatile boolean voicemeeterEnabled;
    private volatile String voicemeeterPath = "C:\\Program Files (x86)\\VB\\Voicemeeter";

    public static Map<String, DeviceSave> getDevices() {
        return save.devices;
    }

    public static DeviceSave getDeviceSave(String serialNum) {
        return save.devices.get(serialNum);
    }

    public static void createSaveForNewDevice(String serialNum, DeviceType dt) {
        getDevices().put(serialNum, new DeviceSave(dt));
    }

    public static boolean doesDeviceDisplayNameExist(String displayName) {
        if (displayName == null)
            throw new IllegalArgumentException("cannot have null displayName");
        for (var device : getDevices().values()) {
            if (displayName.equals(device.getDisplayName()))
                return true;
        }
        return false;
    }

    public static synchronized void saveFile() {
        Main.saveFileExists = true;
        for (var ds : getDevices().values()) {
            var p = ds.getCurrentProfile();
            p.buttonData = ds.buttonData;
            p.dialData = ds.dialData;
            p.lightingConfig = ds.getLightingConfig();
            p.knobSettings = ds.getKnobSettings();
        }
        try {
            FileUtils.writeStringToFile(SAVE_FILE, Json.writePretty(save), Charset.defaultCharset());
        } catch (IOException e) {
            log.error("Unable to save file", e);
        }
    }

    public static void readFile() {
        try {
            save = Json.read(FileUtils.readFileToString(SAVE_FILE, Charset.defaultCharset()), Save.class);
            Main.saveFileExists = true;
        } catch (Exception e) {
            save = new Save();
        }
    }

    public static boolean isObsEnabled() {
        return save.obsEnabled;
    }

    public static void setObsEnabled(boolean obsEnabled) {
        save.obsEnabled = obsEnabled;
    }

    public static String getObsAddress() {
        return save.obsAddress;
    }

    public static void setObsAddress(String obsAddress) {
        save.obsAddress = obsAddress;
    }

    public static String getObsPort() {
        return save.obsPort;
    }

    public static void setObsPort(String obsPort) {
        save.obsPort = obsPort;
    }

    public static String getObsPassword() {
        return save.obsPassword;
    }

    public static void setObsPassword(String obsPassword) {
        save.obsPassword = obsPassword;
    }

    public static boolean isVoicemeeterEnabled() {
        return save.voicemeeterEnabled;
    }

    public static void setVoicemeeterEnabled(boolean voicemeeterEnabled) {
        save.voicemeeterEnabled = voicemeeterEnabled;
    }

    public static String getVoicemeeterPath() {
        return save.voicemeeterPath;
    }

    public static void setVoicemeeterPath(String voicemeeterPath) {
        save.voicemeeterPath = voicemeeterPath;
    }
}
