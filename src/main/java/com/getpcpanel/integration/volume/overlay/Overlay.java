package com.getpcpanel.integration.volume.overlay;

import java.awt.Image;
import java.io.File;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.commands.ButtonFeedbackEvent;
import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.IconService;
import com.getpcpanel.commands.PCPanelControlEvent;
import com.getpcpanel.commands.TakeoverPendingEvent;
import com.getpcpanel.commands.command.ButtonAction;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDevice;
import com.getpcpanel.integration.volume.command.CommandVolumeDefaultDeviceToggle;
import com.getpcpanel.integration.volume.command.CommandVolumeFocus;
import com.getpcpanel.commands.command.DialAction;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.device.Device;
import com.getpcpanel.device.descriptor.AnalogKind;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.BaseLayerService;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.profile.SaveService.SaveEvent;
import com.getpcpanel.profile.dto.KnobSetting;
import com.getpcpanel.profile.dto.LightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig;
import com.getpcpanel.profile.dto.SingleKnobLightingConfig.SINGLE_KNOB_MODE;
import com.getpcpanel.profile.dto.SingleSliderLightingConfig;
import com.getpcpanel.util.coloroverride.OverrideColorService;
import com.getpcpanel.integration.volume.VolumeCoordinatorService;
import com.getpcpanel.template.TemplateContext;
import com.getpcpanel.template.TemplateService;
import com.sun.jna.Platform;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@ApplicationScoped
@RequiredArgsConstructor(access = AccessLevel.PACKAGE) // with the window, for tests
public class Overlay {
    private final SaveService save;
    private final IconService iconService;
    private final Instance<ISndCtrl> sndCtrl;
    private final VolumeCoordinatorService volumeCoordinator;
    private final DeviceHolder deviceHolder;
    private final OverrideColorService overrideColorService;
    private final TemplateService templates;
    private final BaseLayerService baseLayer;
    // The AWT/Swing windowing toolkit is unsupported in the GraalVM native image (it segfaults the
    // native WToolkit event loop), so neither overlay uses it: Windows draws a JNA layered window and
    // Linux/Wayland asks the desktop to draw it over D-Bus (KDE volume OSD, else a notification).
    // macOS still has no AWT-free overlay.
    private final OverlayWindow overlay;

    @Inject
    public Overlay(SaveService save, IconService iconService, Instance<ISndCtrl> sndCtrl, VolumeCoordinatorService volumeCoordinator,
            DeviceHolder deviceHolder, OverrideColorService overrideColorService, TemplateService templates, BaseLayerService baseLayer) {
        this(save, iconService, sndCtrl, volumeCoordinator, deviceHolder, overrideColorService, templates, baseLayer, createOverlay());
    }

    private static OverlayWindow createOverlay() {
        if (Platform.isWindows()) {
            return new Win32VolumeOverlay();
        }
        if (Platform.isLinux()) {
            return new LinuxOverlay();
        }
        return new NoOpOverlayWindow();
    }

    public void updateSaveValues(@Observes SaveEvent event) {
        updateStyle(null);
        determinePosition();
    }

    private void determinePosition() {
        var window = overlay.getScreenSize();
        var x = window.width();
        var y = window.height();
        var width = overlay.getWidth();
        var height = overlay.getHeight();

        var position = save == null ? OverlayPosition.topLeft : save.get().getOverlayPosition();
        var padding = save == null ? 0 : save.get().getOverlayPadding();
        var newY = switch (position) {
            case topLeft, topMiddle, topRight -> padding;
            case middleLeft, middleMiddle, middleRight -> y / 2 - height / 2;
            case bottomLeft, bottomMiddle, bottomRight -> y - overlay.getHeight() - padding;
        };
        var newX = switch (position) {
            case topLeft, middleLeft, bottomLeft -> padding;
            case topMiddle, middleMiddle, bottomMiddle -> x / 2 - width / 2;
            case topRight, middleRight, bottomRight -> x - width - padding;
        };
        setXY(newX, newY);
    }

    private void setXY(int x, int y) {
        overlay.setLocation(x, y);
    }

    public void show(float value) {
        showDebounced(value, () -> CommandAndIcon.DEFAULT, x -> true);
    }

    public void updateStyle(@Nullable @Observes SaveEvent event) {
        overlay.setStyles(save.get());
    }

    public void handleControl(@Observes PCPanelControlEvent event) {
        if (event.initial()) {
            return;
        }
        // The overlay is purely cosmetic, but this observer runs synchronously on the same event
        // notification as CommandDispatcher (the bean that actually performs the volume/mute/etc. action),
        // on the HID input thread. An uncaught Throwable here would abort the notification (so the command
        // never runs) and kill the input thread, freezing ALL hardware control. The icon path depends on
        // AWT/Java2D, which is fragile in the native image, so isolate it: on any failure, log and move on.
        try {
            var value = barValue(event.vol());
            showDebounced(value, () -> determineIconImage(event), this::shouldShow);
        } catch (Throwable t) {
            log.warn("Overlay failed to handle control event; ignoring (hardware control is unaffected)", t);
        }
    }

    /** Where the bar stands for a control reading (0..1), or -1 without one. */
    private float barValue(@Nullable DialValue vol) {
        // The log/linear scale only shapes our own custom-rendered bar (Windows). On Linux the
        // desktop's native OSD shows the true volume, so the scale doesn't apply there (the setting
        // is disabled in the UI) — always report the real linear level.
        var useLog = save.get().isOverlayUseLog() && !Platform.isLinux();
        return vol == null ? -1 : useLog ? vol.getValue(null, 0, 1) : vol.value() / 255f;
    }

    /**
     * A control waiting for soft takeover: the bar follows the control, as it always does, a line marks the control
     * position at which it takes over, and (with {@code Save.overlayTakeoverText}) the name says where to move to —
     * all on the same scale as the overlay's bar, not the target's level after the control's curve.
     */
    public void handleTakeover(@Observes TakeoverPendingEvent event) {
        if (!save.get().isOverlayEnabled()) {
            return;
        }
        try {
            var vol = event.control().vol();
            if (vol == null) {
                return;
            }
            var takeover = barValue(new DialValue(vol.settings(), event.position()));
            var cai = determineIconImage(event.control());
            var name = takeoverName(cai.name, takeover, save.get().isOverlayTakeoverText());
            overlay.show(new OverlayContent(barValue(vol), cai.icon, name, cai.barColorCss, takeover));
        } catch (Throwable t) {
            log.warn("Overlay failed to show a takeover hint; ignoring (hardware control is unaffected)", t);
        }
    }

    /** The overlay's name while a control waits for takeover: its own name, with "move to X% to take over" when {@code withText}. */
    static String takeoverName(@Nullable String name, float current, boolean withText) {
        if (!withText) {
            return StringUtils.defaultString(name).strip();
        }
        var hint = "move to " + Math.round(current * 100) + "% to take over";
        return StringUtils.isBlank(name) ? StringUtils.capitalize(hint) : name.strip() + " · " + hint;
    }

    /**
     * What a button action did ("Spotify · Muted", "Default: Headphones"). Skipped when the button's actions carry an
     * overlay text the user typed: {@link #handleControl} already shows the overlay for that press. The event is fired
     * inside the press's {@link TemplateContext}, which holds the actions that ran.
     */
    public void onButtonFeedback(@Observes ButtonFeedbackEvent event) {
        try {
            var settings = save.get();
            if (!settings.isOverlayEnabled() || !settings.isOverlayButtonFeedback()) {
                return;
            }
            var commands = commandsThatRan(event);
            if (hasTypedOverlayText(commands)) {
                return;
            }
            // Icon decoding needs libawt (Windows only), as in determineIconImage.
            var icon = Platform.isWindows() && commands != null && iconService != null ? iconService.getImageFrom(commands, null) : null;
            overlay.show(new OverlayContent(event.level() == null ? -1 : event.level(), icon, event.text(), null));
        } catch (Throwable t) {
            log.warn("Overlay failed to show button feedback; ignoring (hardware control is unaffected)", t);
        }
    }

    /** The actions of the press that fired {@code event}, or null when it was fired outside that press. */
    @Nullable
    private static Commands commandsThatRan(ButtonFeedbackEvent event) {
        var scope = TemplateContext.current();
        var same = scope.button() && scope.control() == event.button() && event.serial().equals(scope.serial());
        return same ? scope.commands() : null;
    }

    /**
     * Whether a button action carries an overlay text the user typed. The default-device actions derive theirs from
     * the device they switch to, which their feedback already names, so theirs does not count.
     */
    private static boolean hasTypedOverlayText(@Nullable Commands commands) {
        return Commands.hasCommands(commands) && StreamEx.of(commands.getCommands())
                .anyMatch(c -> c instanceof ButtonAction ba && ba.hasOverlay()
                        && !(c instanceof CommandVolumeDefaultDevice) && !(c instanceof CommandVolumeDefaultDeviceToggle));
    }

    private void showDebounced(float value, Supplier<CommandAndIcon> pre, Predicate<Commands> pred) {
        if (!save.get().isOverlayEnabled()) {
            return;
        }
        var cai = pre.get();
        if (hasOverlay(cai.command) && pred.test(cai.command)) {
            overlay.show(new OverlayContent(value, cai.icon, cai.name, cai.barColorCss));
        }
    }

    /**
     * Whether the overlay should be shown for this control. Suppresses it when a focus-volume dial is
     * moved while the focused app is being skipped ("skip controlled apps" left it alone): the dial isn't
     * actually changing anything, so a progress overlay would falsely imply it is. Other commands (and a
     * focus-volume dial that really does control the OS / Wave Link) still show normally.
     */
    private boolean shouldShow(Commands commands) {
        var controlsFocusVolume = StreamEx.of(commands.getCommands()).anyMatch(c -> c instanceof CommandVolumeFocus);
        if (!controlsFocusVolume) {
            return true;
        }
        var app = sndCtrl.isResolvable() ? sndCtrl.get().getFocusApplication() : null;
        return app == null || !volumeCoordinator.wouldSkipFocusVolume(app);
    }

    private boolean hasOverlay(Commands commands) {
        return Commands.hasCommands(commands) &&
                StreamEx.of(commands.getCommands()).anyMatch(command -> command instanceof DialAction da && da.hasOverlay()
                        || command instanceof ButtonAction ba && ba.hasOverlay());
    }

    @Nonnull
    private CommandAndIcon determineIconImage(PCPanelControlEvent event) {
        return save.getProfile(event.serialNum()).map(profile -> {
            var data = event.cmd();
            var setting = event.vol() == null ? null : baseLayer.effectiveKnobSetting(event.serialNum(), profile, event.knob());
            // Icon decoding needs libawt (Windows only); elsewhere the overlay is a no-op that ignores
            // the icon, so skip the BufferedImage lookup entirely to stay libawt-free.
            var icon = Platform.isWindows() ? iconService.getImageFrom(data, setting) : null;
            Supplier<String> detected = () -> targetName(data);
            return new CommandAndIcon(data, icon, overlayName(setting, detected, (template, name) -> {
                var scope = event.templateScope().withName(name);
                var percent = event.vol() == null ? null : Math.round(event.vol().getValue(null, 0f, 100f));
                return templates.render(template, scope.withValue(percent), () -> template);
            }), barColorFromLight(event));
        }).orElse(CommandAndIcon.DEFAULT);
    }

    /**
     * The name the overlay shows: the per-control override a user typed ({@link KnobSetting#getOverlayName()}),
     * or the name derived from the control's actions when that is blank. {@code detected} is only resolved on
     * the fallback path or when the override's template uses {@code {{ name }}}, since deriving it can query the
     * OS for the focused application.
     */
    static String overlayName(@Nullable KnobSetting setting, Supplier<String> detected, BiFunction<String, Supplier<String>, String> render) {
        var override = setting == null ? null : setting.getOverlayName();
        if (StringUtils.isBlank(override)) {
            return detected.get();
        }
        var text = StringUtils.strip(override);
        return TemplateService.hasTags(text) ? StringUtils.strip(render.apply(text, detected)) : text;
    }

    static String overlayName(@Nullable KnobSetting setting, Supplier<String> detected) {
        return overlayName(setting, detected, (template, name) -> template);
    }

    /** The bar colour to use, sourced from the moved control's current light, when "bar follows light"
     *  is on; else null. Handles both knobs and sliders (a moved slider also reports source DIAL, but its
     *  index lives in the combined analog space — 5..8 on a Pro — and its light lives in a separate
     *  config array). Animated modes (rainbow/wave/breath) have no single colour and fall back to null. */
    @Nullable
    private String barColorFromLight(PCPanelControlEvent event) {
        if (!save.get().isOverlayBarFollowsLight() || event.source() != PCPanelControlEvent.Source.DIAL) {
            return null;
        }
        var device = deviceHolder.getDevice(event.serialNum()).orElse(null);
        if (device == null || device.lightingConfig() == null) {
            return null;
        }
        var spec = StreamEx.of(device.descriptor().analogInputs()).findFirst(a -> a.index() == event.knob()).orElse(null);
        if (spec == null) {
            return null;
        }
        var lighting = device.lightingConfig();
        // The lighting config arrays are indexed per-kind (knob 0.., slider 0..), not by the combined index.
        var ordinal = analogOrdinal(device, event.knob(), spec.kind());
        return spec.kind() == AnalogKind.SLIDER
                ? sliderLight(event.serialNum(), lighting, ordinal)
                : knobLight(event.serialNum(), lighting, event.knob(), ordinal);
    }

    /** Position of {@code combinedIndex} among analog inputs of the same {@code kind} (its config-array index). */
    private static int analogOrdinal(Device device, int combinedIndex, AnalogKind kind) {
        return (int) StreamEx.of(device.descriptor().analogInputs())
                             .filter(a -> a.kind() == kind && a.index() < combinedIndex).count();
    }

    @Nullable
    private String knobLight(String serial, LightingConfig lighting, int combinedIndex, int ordinal) {
        var override = overrideColorService.getDialOverride(serial, ordinal).orElse(null); // mute/other overrides win
        if (override != null) {
            return staticColor(override);
        }
        return switch (lighting.lightingMode()) {
            case ALL_COLOR -> lighting.allColor();
            case SINGLE_COLOR -> atIndex(lighting.individualColors(), combinedIndex);
            case CUSTOM -> ordinal < lighting.knobConfigs().length ? staticColor(lighting.knobConfigs()[ordinal]) : null;
            case ALL_RAINBOW, ALL_WAVE, ALL_BREATH -> null;
        };
    }

    @Nullable
    private String sliderLight(String serial, LightingConfig lighting, int ordinal) {
        var override = overrideColorService.getSliderOverride(serial, ordinal).orElse(null);
        if (override != null) {
            return sliderStaticColor(override);
        }
        return switch (lighting.lightingMode()) {
            case ALL_COLOR -> lighting.allColor();
            case CUSTOM -> ordinal < lighting.sliderConfigs().length ? sliderStaticColor(lighting.sliderConfigs()[ordinal]) : null;
            case SINGLE_COLOR, ALL_RAINBOW, ALL_WAVE, ALL_BREATH -> null;
        };
    }

    /** The base colour of a static/gradient knob config, or null when the knob light is off. */
    @Nullable
    private static String staticColor(SingleKnobLightingConfig config) {
        return config.getMode() == SINGLE_KNOB_MODE.NONE ? null : config.getColor1();
    }

    /** The base colour of a static/gradient slider config, or null when the slider light is off. */
    @Nullable
    private static String sliderStaticColor(SingleSliderLightingConfig config) {
        return config.getMode() == SingleSliderLightingConfig.SINGLE_SLIDER_MODE.NONE ? null : config.getColor1();
    }

    @Nullable
    private static String atIndex(@Nullable String[] colors, int index) {
        return colors != null && index >= 0 && index < colors.length ? colors[index] : null;
    }

    /** A best-effort name of what the control affects, for the overlay's app-name line. */
    private String targetName(Commands commands) {
        return StreamEx.of(commands.getCommands())
                       .map(this::nameOf)
                       .findFirst(StringUtils::isNotBlank)
                       .orElse("");
    }

    private String nameOf(Command command) {
        if (command instanceof CommandVolumeFocus) {
            // Focus volume targets the foreground app; Wave Link's friendly name isn't available here, so
            // use the executable's base name without its extension (e.g. msedge.exe → msedge).
            var app = sndCtrl.isResolvable() ? sndCtrl.get().getFocusApplication() : null;
            return app == null ? "" : StringUtils.removeEndIgnoreCase(new File(app).getName(), ".exe");
        }
        return command.buildLabel();
    }

    private record CommandAndIcon(Commands command, Image icon, String name, @Nullable String barColorCss) {
        static final CommandAndIcon DEFAULT = new CommandAndIcon(Commands.EMPTY, null, "", null);
    }
}
