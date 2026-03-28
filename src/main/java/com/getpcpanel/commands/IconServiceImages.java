package com.getpcpanel.commands;

import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;
import javafx.scene.image.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor
public class IconServiceImages {
    public static final Image DEFAULT = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/32x32.png")).toExternalForm());
    static final Image OBS = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/obs.png")).toExternalForm());
    static final Image VOICEMEETER = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/voicemeeter.png")).toExternalForm());
    static final Image DEVICE = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/device.png")).toExternalForm());
    static final Image SYSTEM_SOUND = new Image(Objects.requireNonNull(IconServiceImages.class.getResource("/assets/systemsounds.ico")).toExternalForm());
}
