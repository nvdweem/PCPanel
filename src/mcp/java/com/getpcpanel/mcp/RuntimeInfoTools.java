package com.getpcpanel.mcp;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.device.provider.DeviceProvider;
import com.getpcpanel.integration.mqtt.MqttService;
import com.getpcpanel.integration.obs.OBS;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.io.FileUtil;
import com.getpcpanel.integration.voicemeeter.Voicemeeter;
import com.getpcpanel.integration.wavelink.WaveLinkService;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkiverse.mcp.server.Tool;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Read-only runtime introspection: the single call an agent makes first to learn what this running
 * instance is (version, JVM vs native build, OS, data root, loaded device providers, integration
 * connectivity) without HTTP-probing {@code /q/health} and several {@code /api/*} endpoints.
 */
@Log4j2
@ApplicationScoped
@IfBuildProperty(name = McpDevTool.FLAG, stringValue = "true")
public class RuntimeInfoTools {
    @Inject FileUtil fileUtil;
    @Inject SaveService saveService;
    @Inject Instance<DeviceProvider> providers;
    @Inject Instance<OBS> obs;
    @Inject Instance<WaveLinkService> waveLink;
    @Inject Instance<Voicemeeter> voicemeeter;
    @Inject Instance<MqttService> mqtt;

    @ConfigProperty(name = "pcpanel.version", defaultValue = "unknown") String version;
    @ConfigProperty(name = "pcpanel.build.os", defaultValue = "unknown") String buildOs;
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "7654") int httpPort;

    @Tool(description = "PCPanel runtime info: app version, build flavor (native|jvm), OS, data root, "
            + "HTTP port, loaded device providers, and integration connection status. Call this first "
            + "to learn what the running instance is.")
    public RuntimeInfo pcpanel_runtime_info() {
        var providerInfos = StreamEx.of(providers.iterator())
                                    .map(p -> new ProviderInfo(safeId(p), safeMode(p)))
                                    .toList();
        var save = saveService.get();
        return new RuntimeInfo(
                version,
                System.getProperty("org.graalvm.nativeimage.imagecode") != null ? "native" : "jvm",
                System.getProperty("os.name"),
                buildOs,
                fileUtil.getRoot().getAbsolutePath(),
                httpPort,
                providerInfos,
                new Integrations(
                        integration(obs, save.isObsEnabled(), OBS::isConnected),
                        integration(waveLink, save.getWaveLink().enabled(), WaveLinkService::isConnected),
                        // Voicemeeter exposes no isConnected(); a cached non-null version means it has
                        // completed a login at least once - the best available connectivity proxy.
                        integration(voicemeeter, save.isVoicemeeterEnabled(), b -> b.getVersion() != null),
                        integration(mqtt, save.getMqtt().enabled(), MqttService::isConnected)),
                waveLinkFocus());
    }

    /** Diagnostic: what Wave Link reports as the focused app, and the channel the focus-volume logic resolves it to. */
    private WaveLinkFocus waveLinkFocus() {
        if (!waveLink.isResolvable()) {
            return new WaveLinkFocus(null, null, null, java.util.Map.of());
        }
        try {
            var wl = waveLink.get();
            var app = wl.getLastFocusApp();
            return new WaveLinkFocus(app == null ? null : app.id(), app == null ? null : app.name(),
                    wl.findChannelIdForFocusApp().orElse(null), wl.focusIdentitySnapshot());
        } catch (RuntimeException e) {
            log.debug("Wave Link focus probe failed: {}", e.getMessage());
            return new WaveLinkFocus(null, null, null, java.util.Map.of());
        }
    }

    private static String safeId(DeviceProvider p) {
        try {
            return p.id();
        } catch (RuntimeException e) {
            return p.getClass().getSimpleName();
        }
    }

    private static String safeMode(DeviceProvider p) {
        try {
            return String.valueOf(p.discoveryMode());
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static <T> IntegrationStatus integration(Instance<T> bean, boolean enabled,
            java.util.function.Predicate<T> connected) {
        if (!bean.isResolvable()) {
            return new IntegrationStatus(false, false, false);
        }
        try {
            return new IntegrationStatus(true, enabled, connected.test(bean.get()));
        } catch (RuntimeException e) {
            log.debug("Integration status probe failed: {}", e.getMessage());
            return new IntegrationStatus(true, enabled, false);
        }
    }

    public record RuntimeInfo(
            String version,
            String build,
            String os,
            String buildOs,
            String pcpanelRoot,
            int httpPort,
            List<ProviderInfo> providersLoaded,
            Integrations integrations,
            WaveLinkFocus waveLinkFocus) {
    }

    public record ProviderInfo(String id, String discoveryMode) {
    }

    /** Wave Link's currently focused app (as it reports it), the channel focus-volume resolves it to, and
     *  the learned OS-process → Wave Link app-name map that bridges executables to Wave Link's app names. */
    public record WaveLinkFocus(String focusAppId, String focusAppName, String resolvedChannelId,
            java.util.Map<String, String> learnedIdentities) {
    }

    public record Integrations(IntegrationStatus obs, IntegrationStatus waveLink, IntegrationStatus voicemeeter,
            IntegrationStatus mqtt) {
    }

    /** {@code available}: the bean exists in this (OS-specific) build at all. */
    public record IntegrationStatus(boolean available, boolean enabled, boolean connected) {
    }
}
