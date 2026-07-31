package com.getpcpanel.report;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.rest.PlatformResource;
import com.getpcpanel.util.io.FileUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The facts about a running install that a reporter can never be expected to supply accurately:
 * exact build, how it was packaged, which devices are actually connected and which integrations are
 * switched on. Rendered as plain text so it stays readable pasted into an issue.
 *
 * <p>Only the on/off state of each integration is reported, never its configuration — the
 * configuration is a separate, opt-in attachment that goes through {@link ProfileRedactor}.
 */
@ApplicationScoped
public class SystemInfoCollector {
    @Inject PlatformResource platform;
    @Inject DeviceHolder devices;
    @Inject SaveService saveService;
    @Inject FileUtil fileUtil;

    public String collect() {
        var info = platform.get();
        var save = saveService.get();
        var out = new StringBuilder();

        out.append("PCPanel — system information\n\n");

        out.append("Application\n");
        append(out, "version", info.version());
        if (StringUtils.isNotBlank(info.branch()) || StringUtils.isNotBlank(info.commit())) {
            append(out, "build", StringUtils.defaultString(info.branch()) + " @ " + StringUtils.defaultString(info.commit()));
        }
        append(out, "packaging", packaging(info.flatpak()));
        append(out, "runtime", runtime());
        append(out, "auto update", String.valueOf(info.autoUpdate()));
        append(out, "data directory", String.valueOf(fileUtil.getRoot()));

        out.append("\nOperating system\n");
        append(out, "reported as", info.os());
        append(out, "name", System.getProperty("os.name"));
        append(out, "version", System.getProperty("os.version"));
        append(out, "architecture", System.getProperty("os.arch"));

        var connected = devices.all();
        out.append("\nDevices (").append(connected.size()).append(" connected)\n");
        if (connected.isEmpty()) {
            out.append("  none\n");
        }
        for (var device : connected) {
            var descriptor = device.descriptor();
            out.append("  - ").append(descriptor.displayName())
               .append(" [").append(descriptor.providerId()).append('/').append(descriptor.deviceKindId()).append(']')
               .append(" serial ").append(device.getSerialNumber()).append('\n');
        }

        out.append("\nIntegrations enabled\n");
        append(out, "obs", String.valueOf(save.isObsEnabled()));
        append(out, "voicemeeter", String.valueOf(save.isVoicemeeterEnabled()));
        append(out, "osc", String.valueOf(save.isOscEnabled()));
        append(out, "wave link", String.valueOf(save.getWaveLink() != null && save.getWaveLink().enabled()));
        append(out, "mqtt", String.valueOf(save.getMqtt() != null && save.getMqtt().enabled()));
        append(out, "discord", String.valueOf(save.getDiscord() != null && save.getDiscord().enabled()));
        append(out, "home assistant servers", String.valueOf(save.getHomeAssistantServers() == null ? 0 : save.getHomeAssistantServers().size()));
        append(out, "overlay", String.valueOf(save.isOverlayEnabled()));

        return out.toString();
    }

    private static String packaging(boolean flatpak) {
        if (flatpak) {
            return "flatpak";
        }
        return StringUtils.isNotBlank(System.getenv("APPIMAGE")) ? "appimage" : "native/installed";
    }

    private static String runtime() {
        var image = System.getProperty("org.graalvm.nativeimage.imagecode");
        return StringUtils.isNotBlank(image) ? "native image" : "JVM " + System.getProperty("java.version");
    }

    private static void append(StringBuilder out, String label, String value) {
        out.append("  ").append(StringUtils.rightPad(label + ':', 24)).append(StringUtils.defaultString(value, "unknown")).append('\n');
    }
}
