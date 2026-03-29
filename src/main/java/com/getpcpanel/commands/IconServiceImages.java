package com.getpcpanel.commands;

import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import javafx.scene.image.Image;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
public class IconServiceImages {
    private static Image DEFAULT;
    private static Image OBS;
    private static Image VOICEMEETER;
    private static Image DEVICE;
    private static Image SYSTEM_SOUND;

    public static Image getDefault() {
        if (DEFAULT == null) {
            DEFAULT = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/32x32.png")).toExternalForm());
        }
        return DEFAULT;
    }

    public static Image getObs() {
        if (OBS == null) {
            OBS = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/obs.png")).toExternalForm());
        }
        return OBS;
    }

    public static Image getVoicemeeter() {
        if (VOICEMEETER == null) {
            VOICEMEETER = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/voicemeeter.png")).toExternalForm());
        }
        return VOICEMEETER;
    }

    public static Image getDevice() {
        if (DEVICE == null) {
            DEVICE = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/device.png")).toExternalForm());
        }
        return DEVICE;
    }

    public static Image getSystemSound() {
        if (SYSTEM_SOUND == null) {
            SYSTEM_SOUND = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/systemsounds.ico")).toExternalForm());
        }
        return SYSTEM_SOUND;
    }
}
