package com.getpcpanel.integration.display.platform.linux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.display.DdcOffCode;
import com.getpcpanel.integration.display.DdcToggle;
import com.getpcpanel.integration.display.DisplayNames;
import com.getpcpanel.integration.display.DisplayPower;
import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * Puts the displays to sleep the way that wakes them again on mouse or keyboard input: KDE Plasma's
 * {@code kscreen-doctor} (Wayland and X11), else X11's DPMS through {@code xset}. {@code xset} is skipped on Wayland,
 * where it would only reach XWayland and report success without turning anything off. Other Wayland desktops are left
 * out on purpose: Hyprland's and Sway's own commands keep the displays off until told otherwise, so a press would leave
 * the user in the dark.
 *
 * <p>Single monitors are switched over DDC/CI with {@code ddcutil}, addressed by I2C bus number; it needs read/write
 * access to {@code /dev/i2c-*}, which the {@code i2c} group usually grants. In the Flatpak all three tools run on the
 * host through wrappers.
 */
@Log4j2
@Unremovable
@LinuxBuild
@ApplicationScoped
class LinuxDisplayPower implements DisplayPower {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    /** {@code detect} probes every I2C bus and {@code capabilities} reads a long reply, waiting out DDC/CI's delays. */
    private static final Duration DDC_SLOW_TIMEOUT = Duration.ofSeconds(15);
    /** One VCP read or write, on the command thread. */
    private static final Duration DDC_TIMEOUT = Duration.ofSeconds(4);
    private static final Pattern BUS = Pattern.compile("/dev/i2c-(\\d+)");
    private static final Pattern CURRENT_VALUE = Pattern.compile("sl=0x([0-9a-fA-F]{2})");

    @Inject ProcessHelper processes;
    /** Off code per bus; capabilities take a second or more to read and do not change. */
    private final Map<String, Integer> offCodes = new ConcurrentHashMap<>();
    private final DdcToggle toggle = new DdcToggle();
    /** Buses whose capabilities did not come back this session; {@link #list()} does not ask them again. */
    private final Set<String> unreadable = ConcurrentHashMap.newKeySet();
    /** Runs the capability reads {@link #list()} starts, so the command thread rarely has to. */
    Executor warmer = r -> Thread.ofPlatform().daemon().name("ddc-capabilities").start(r);
    /** Set while a capability read started by {@link #list()} runs, so there is never more than one. */
    private final AtomicBoolean warming = new AtomicBoolean();

    @Override
    public void turnOff() {
        for (var command : commands(System.getenv())) {
            if (tryRun(command)) {
                return;
            }
        }
        log.warn("Unable to turn the displays off: this needs KDE Plasma (kscreen-doctor) or an X11 session (xset)");
    }

    /** The commands to try, in order, for a session with this environment. */
    static List<String[]> commands(Map<String, String> env) {
        var result = new ArrayList<String[]>();
        result.add(new String[] { "kscreen-doctor", "--dpms", "off" });
        if (!wayland(env)) {
            result.add(new String[] { "xset", "dpms", "force", "off" });
        }
        return result;
    }

    @Override
    public List<DisplayInfo> list() {
        var result = ddcutil(DDC_SLOW_TIMEOUT, "detect", "--brief");
        var displays = result == null ? List.<DisplayInfo>of() : parseDetect(result.stdout());
        var cold = displays.stream().map(DisplayInfo::id).filter(bus -> !offCodes.containsKey(bus) && !unreadable.contains(bus)).toList();
        if (!cold.isEmpty() && warming.compareAndSet(false, true)) {
            warmer.execute(() -> {
                try {
                    cold.forEach(this::warm);
                } finally {
                    warming.set(false);
                }
            });
        }
        return displays;
    }

    /**
     * Reads the capabilities of the display on {@code bus} unless they are known or did not come back before. Holds
     * the lock for this one display only, so a press waits for at most one capability read.
     */
    private synchronized void warm(String bus) {
        if (!offCodes.containsKey(bus) && !unreadable.contains(bus)) {
            offCode(bus);
        }
    }

    @Override
    public synchronized void toggle(List<String> ids) {
        var modes = new LinkedHashMap<String, Integer>();
        for (var bus : ids) {
            modes.put(bus, powerMode(bus));
        }
        var plan = toggle.plan(modes);
        for (var bus : plan.ids()) {
            var code = plan.turnOff() ? offCode(bus) : DdcOffCode.ON;
            if (ddcutil(DDC_TIMEOUT, "setvcp", "d6", Integer.toString(code), "--bus", bus) == null) {
                log.warn("Unable to turn display on I2C bus {} {}", bus, plan.turnOff() ? "off" : "on");
            } else {
                toggle.switched(bus, !plan.turnOff());
            }
        }
    }

    /**
     * Displays in {@code ddcutil detect} output, named "LG HDR WQHD · Display 1" by the model the monitor reports;
     * skips the ones it lists as invalid (no DDC/CI).
     */
    static List<DisplayInfo> parseDetect(List<String> lines) {
        var result = new ArrayList<DisplayInfo>();
        String title = null;
        String bus = null;
        String model = null;
        for (var raw : lines) {
            var line = raw.strip();
            if (line.startsWith("Display ") || line.startsWith("Invalid display")) {
                add(result, title, bus, model);
                title = line.startsWith("Display ") ? line : null;
                bus = null;
                model = null;
            } else if (line.startsWith("I2C bus:")) {
                var m = BUS.matcher(line);
                bus = m.find() ? m.group(1) : null;
            } else if (line.startsWith("Monitor:")) {
                // mfg:model:serial
                var parts = StringUtils.substringAfter(line, ":").strip().split(":", -1);
                if (parts.length > 1 && StringUtils.isNotBlank(parts[1])) {
                    model = parts[1].strip();
                }
            } else if (line.startsWith("Model:") && model == null) {
                // the EDID synopsis of a detect without --brief
                model = StringUtils.trimToNull(StringUtils.substringAfter(line, ":"));
            }
        }
        add(result, title, bus, model);
        return result;
    }

    private static void add(List<DisplayInfo> result, @Nullable String title, @Nullable String bus, @Nullable String model) {
        if (title != null && bus != null) {
            var digits = StringUtils.getDigits(title);
            result.add(new DisplayInfo(bus, DisplayNames.format(model, digits.isEmpty() ? 0 : Integer.parseInt(digits), 0, 0)));
        }
    }

    /** The power mode the display on {@code bus} reports; null when it does not answer. */
    private @Nullable Integer powerMode(String bus) {
        var result = ddcutil(DDC_TIMEOUT, "getvcp", "d6", "--bus", bus);
        if (result == null) {
            log.info("Display on I2C bus {} does not answer over DDC/CI: is it connected, with DDC/CI on in its menu?", bus);
            return null;
        }
        var m = CURRENT_VALUE.matcher(String.join("\n", result.stdout()));
        if (!m.find()) {
            log.warn("Unexpected power mode answer from the display on I2C bus {}: {}", bus, result.stdout());
            return null;
        }
        return Integer.parseInt(m.group(1), 16);
    }

    /** The off code for the display on {@code bus}; remembered only once its capabilities were read. */
    private int offCode(String bus) {
        var known = offCodes.get(bus);
        if (known != null) {
            return known;
        }
        var result = ddcutil(DDC_SLOW_TIMEOUT, "capabilities", "--bus", bus, "--verbose");
        if (result == null) {
            unreadable.add(bus);
            return DdcOffCode.choose(null);
        }
        unreadable.remove(bus);
        var code = DdcOffCode.choose(String.join("\n", result.stdout()));
        offCodes.put(bus, code);
        return code;
    }

    /** Runs ddcutil; null when it failed, after logging why. */
    private @Nullable ProcessHelper.Result ddcutil(Duration timeout, String... args) {
        var command = new String[args.length + 1];
        command[0] = "ddcutil";
        System.arraycopy(args, 0, command, 1, args.length);
        try {
            var result = processes.run(timeout, ProcessHelper.PARSEABLE_OUTPUT, command);
            if (result.succeeded()) {
                return result;
            }
            var output = String.join("\n", result.stderr()) + "\n" + String.join("\n", result.stdout());
            if (StringUtils.containsAnyIgnoreCase(output, "permission denied", "EACCES", "not readable", "not writable")) {
                log.warn("ddcutil cannot open /dev/i2c-*: add your user to the i2c group (sudo usermod -aG i2c $USER) and log in again");
            } else {
                log.debug("ddcutil {} failed ({}): {}", args[0], result.timedOut() ? "timed out" : "exit " + result.exitCode(), output.strip());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("ddcutil {} failed: {}", args[0], e.toString());
        }
        return null;
    }

    private static boolean wayland(Map<String, String> env) {
        return "wayland".equalsIgnoreCase(env.get("XDG_SESSION_TYPE")) || StringUtils.isNotBlank(env.get("WAYLAND_DISPLAY"));
    }

    private boolean tryRun(String... command) {
        try {
            return processes.run(TIMEOUT, command).succeeded();
        } catch (Exception e) {
            log.debug("{} failed: {}", command[0], e.toString());
            return false;
        }
    }
}
