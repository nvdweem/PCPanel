package com.getpcpanel.integration.display.platform.linux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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
 * the user in the dark. In the Flatpak both tools run on the host through wrappers.
 */
@Log4j2
@Unremovable
@LinuxBuild
@ApplicationScoped
class LinuxDisplayPower implements DisplayPower {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Inject ProcessHelper processes;

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
