package com.getpcpanel.profile;

import static com.getpcpanel.Main.FILES_ROOT;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;

import com.getpcpanel.Json;
import com.getpcpanel.Main;
import com.getpcpanel.device.DeviceType;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Data
@Log4j2
public class Save {
    private static Save save = new Save();
    private static final File SAVE_FILE = new File(FILES_ROOT, "profiles.json");
    private Map<String, DeviceSave> devices = new ConcurrentHashMap<>();
    private boolean obsEnabled;
    private String obsAddress = "localhost";
    private String obsPort = "4444";
    private String obsPassword;
    private boolean voicemeeterEnabled;
    private String voicemeeterPath = "C:\\Program Files (x86)\\VB\\Voicemeeter";

    public static Save get() {
        return save;
    }

    public static DeviceSave getDeviceSave(String serialNum) {
        return save.devices.get(serialNum);
    }

    public static void createSaveForNewDevice(String serialNum, DeviceType dt) {
        get().getDevices().put(serialNum, new DeviceSave(dt));
    }

    public static boolean doesDeviceDisplayNameExist(String displayName) {
        if (displayName == null)
            throw new IllegalArgumentException("cannot have null displayName");
        for (var device : get().getDevices().values()) {
            if (displayName.equals(device.getDisplayName()))
                return true;
        }
        return false;
    }

    public static synchronized void saveFile() {
        Main.saveFileExists = true;
        for (var ds : get().getDevices().values()) {
            var p = ds.getCurrentProfile();
            p.setButtonData(ds.buttonData);
            p.setDialData(ds.dialData);
            p.setLightingConfig(ds.getLightingConfig());
            p.setKnobSettings(ds.getKnobSettings());
        }
        try {
            FileUtils.writeStringToFile(SAVE_FILE, Json.writePretty(save), Charset.defaultCharset());
        } catch (IOException e) {
            log.error("Unable to save file", e);
        }
    }

    public static void readFile() {
        if (!SAVE_FILE.exists()) {
            log.info("No save file found, creating new one");
            save = new Save();
            return;
        }

        try {
            save = Json.read(FileUtils.readFileToString(SAVE_FILE, Charset.defaultCharset()), Save.class);
            StreamEx.ofValues(save.devices).forEach(d -> StreamEx.of(d.getProfiles()).findFirst(Profile::isMainProfile).ifPresent(p -> d.setCurrentProfile(p.getName())));
            Main.saveFileExists = true;
        } catch (Exception e) {
            log.error("Unable to read file", e);
            save = new Save();
        }
    }
}

