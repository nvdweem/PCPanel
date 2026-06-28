package com.getpcpanel.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.volume.VolumeCoordinatorService;
import com.getpcpanel.integration.wavelink.WaveLinkAppCache;
import com.getpcpanel.integration.wavelink.WaveLinkService;

import dev.niels.wavelink.impl.model.WaveLinkApp;
import dev.niels.wavelink.impl.model.WaveLinkChannel;
import one.util.streamex.StreamEx;

/**
 * Always-on diagnostic for the focus-volume decision (redirect / "skip controlled apps"). It lets the
 * behaviour be inspected on the shipped native build — where the dev MCP harness is compiled out — for
 * an arbitrary app path, so the focused window doesn't have to be the thing under test.
 *
 * <p>{@code GET /api/focus-volume/diagnostics} reports the live focus app; {@code ?app=<exe path>}
 * reports the decision for that path instead. All values are plain strings/booleans/maps/lists so the
 * response needs no extra native reflection.
 */
@Path("/api/focus-volume")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class FocusVolumeDiagnosticsResource {
    @Inject Instance<ISndCtrl> sndCtrl;
    @Inject SaveService save;
    @Inject VolumeCoordinatorService coordinator;
    @Inject WaveLinkService waveLink;
    @Inject WaveLinkAppCache appCache;

    @GET
    @Path("/diagnostics")
    public Map<String, Object> diagnostics(@QueryParam("app") String app) {
        var out = new LinkedHashMap<String, Object>();

        var focus = sndCtrl.isResolvable() ? sndCtrl.get().getFocusApplication() : null;
        var target = app != null && !app.isBlank() ? app : focus;
        out.put("liveFocusApplication", focus);
        out.put("queriedApp", target);
        out.put("normalizedKey", target == null ? null : WaveLinkAppCache.normalizeKey(target));

        var settings = save.get();
        var s = new LinkedHashMap<String, Object>();
        s.put("skipControlledFocusApps", settings.isSkipControlledFocusApps());
        s.put("waveLinkEnabled", settings.getWaveLink().enabled());
        s.put("focusVolumeRedirect", settings.getWaveLink().focusVolumeRedirect());
        out.put("settings", s);

        var lastFocus = waveLink.getLastFocusApp();
        var wl = new LinkedHashMap<String, Object>();
        wl.put("connected", waveLink.isConnected());
        wl.put("lastFocusApp", describeApp(lastFocus));
        wl.put("learnedIdentities", waveLink.focusIdentitySnapshot());
        wl.put("persistedIdentities", appCache.identitySnapshot());
        wl.put("persistedIdentityForQueried", target == null ? null : describeApp(appCache.identity(target)));
        wl.put("channels", channelDump());
        out.put("waveLink", wl);

        var decision = new LinkedHashMap<String, Object>();
        if (target != null) {
            decision.put("focusVolumeTarget", coordinator.focusVolumeTarget(target).orElse(null)); // redirector that claims it
            decision.put("waveLinkManagesApp", waveLink.managesFocusApp(target)); // app is in a live channel
            decision.put("waveLinkControlsApp", waveLink.controlsFocusApp(target)); // redirect would claim it
            decision.put("wouldSkipFocusVolume", coordinator.wouldSkipFocusVolume(target)); // "skip controlled apps" result
        }
        out.put("decision", decision);
        return out;
    }

    private Map<String, Object> channelDump() {
        var result = new LinkedHashMap<String, Object>();
        StreamEx.ofValues(waveLink.getChannels()).forEach(ch -> {
            var entry = new LinkedHashMap<String, Object>();
            entry.put("name", String.valueOf(ch.name()));
            entry.put("apps", StreamEx.of(ch.apps()).map(a -> a.id() + " | " + a.name()).toList());
            result.put(ch.id(), entry);
        });
        return result;
    }

    private static String describeApp(WaveLinkApp app) {
        return app == null ? null : app.id() + " | " + app.name();
    }
}
