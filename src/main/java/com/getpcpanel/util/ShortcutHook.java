package com.getpcpanel.util;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.profile.Profile;
import com.getpcpanel.profile.SaveService;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

@Log4j2
@Service
@RequiredArgsConstructor
public class ShortcutHook implements NativeKeyListener {
    public static final Set<Integer> modifiers = Set.of(NativeKeyEvent.VC_SHIFT, NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_META, NativeKeyEvent.VC_ALT);
    private final SaveService saveService;
    private final DeviceHolder deviceHolder;
    @Setter private Consumer<NativeKeyEvent> overrideListener;
    private Map<String, DeviceProfile> shortcuts;

    @PostConstruct
    public void init() {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException ex) {
            log.error("Unable to register global hook, shortcuts will not work", ex);
        }

        updateShortcuts();
    }

    @PreDestroy
    public void destroy() {
        GlobalScreen.removeNativeKeyListener(this);
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            log.error("Unable to unregister hook");
        }
    }

    public boolean canBeShortcut(NativeKeyEvent event) {
        return event.getModifiers() != 0 && !modifiers.contains(event.getKeyCode());
    }

    public String toKeyString(NativeKeyEvent event) {
        var modifiers = NativeInputEvent.getModifiersText(event.getModifiers());
        var key = NativeKeyEvent.getKeyText(event.getKeyCode());
        return String.join("+", modifiers, key);
    }

    @EventListener(SaveService.SaveEvent.class)
    public void updateShortcuts() {
        shortcuts = EntryStream.of(saveService.get().getDevices())
                               .flatMapValues(ds -> ds.getProfiles().stream())
                               .filterValues(p -> StringUtils.isNotBlank(p.getActivationShortcut()))
                               .mapKeyValue(DeviceProfile::new)
                               .mapToEntry(dp -> dp.profile.getActivationShortcut(), Function.identity())
                               .toMap();
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) {
        if (overrideListener != null) {
            overrideListener.accept(nativeKeyEvent);
            return;
        }

        if (canBeShortcut(nativeKeyEvent)) {
            var profile = shortcuts.get(toKeyString(nativeKeyEvent));
            if (profile != null) {
                Platform.runLater(() -> deviceHolder.getDevice(profile.deviceId()).setProfile(profile.profile.getName()));
            }
        }
    }

    private record DeviceProfile(String deviceId, Profile profile) {
    }
}
