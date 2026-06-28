package com.getpcpanel.profile;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import jakarta.enterprise.context.ApplicationScoped;

import com.getpcpanel.hid.DeviceHolder;
import com.getpcpanel.platform.WindowsBuild;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.EntryStream;

@Log4j2
@ApplicationScoped
@WindowsBuild
@RequiredArgsConstructor
public class ShortcutHook implements NativeKeyListener {
    public static final Set<Integer> modifiers = Set.of(NativeKeyEvent.VC_SHIFT, NativeKeyEvent.VC_CONTROL, NativeKeyEvent.VC_META, NativeKeyEvent.VC_ALT);
    private final SaveService saveService;
    private final DeviceHolder deviceHolder;
    @Setter private Consumer<NativeKeyEvent> overrideListener;
    private Map<String, DeviceProfile> shortcuts;

    @SuppressWarnings("ErrorNotRethrown")
    @PostConstruct
    public void init() {
        if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            // jnativehook loads a JNI library that delivers key events via native→Java callbacks,
            // neither of which works in the GraalVM native image. Skip registration cleanly (rather
            // than failing with an UnsatisfiedLinkError) so global shortcuts are simply unavailable
            // in the native build; they still work in JVM mode.
            log.warn("Global keyboard shortcuts are unavailable in the native image; skipping.");
            updateShortcuts();
            return;
        }
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            log.info("Keyboard hook enabled");
        } catch (NativeHookException | UnsatisfiedLinkError ex) {
            log.error("Unable to register global hook, shortcuts will not work", ex);
        }

        updateShortcuts();
    }

    @PreDestroy
    public void destroy() {
        if ("runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return;
        }
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

    public void onSaveChanged(@Observes SaveService.SaveEvent event) {
        updateShortcuts();
    }

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
                deviceHolder.getDevice(profile.deviceId()).ifPresent(device -> device.switchProfile(profile.profile.getName()));
            }
        }
    }

    private record DeviceProfile(String deviceId, Profile profile) {
    }
}
