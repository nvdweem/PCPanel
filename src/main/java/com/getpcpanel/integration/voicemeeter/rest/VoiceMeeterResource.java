package com.getpcpanel.integration.voicemeeter.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.voicemeeter.Voicemeeter;
import com.getpcpanel.integration.voicemeeter.Voicemeeter.ControlType;
import com.getpcpanel.integration.voicemeeter.VoicemeeterVersion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import one.util.streamex.StreamEx;

/**
 * The parameters of the running Voicemeeter, one group per strip and bus, for the advanced command pickers.
 * Every list is empty while Voicemeeter is disabled or not connected, which the UI reads as "not connected".
 */
@Path("/api/voicemeeter")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class VoiceMeeterResource {
    @Inject Voicemeeter voicemeeter;

    public static class VoiceMeeterParam {
        public String name;
        public List<String> params;

        public VoiceMeeterParam(String name, List<String> params) {
            this.name = name;
            this.params = params;
        }
    }

    @GET
    @Path("/basic")
    public List<VoiceMeeterParam> getBasicParams() {
        return List.of();
    }

    /** Dial parameters (gain, comp, EQ, ...) of every strip and bus. */
    @GET
    @Path("/advanced")
    public List<VoiceMeeterParam> getAdvancedParams() {
        return groups(false);
    }

    /** Button parameters (mute, A1..B3, solo, ...) of every strip and bus. */
    @GET
    @Path("/advanced-buttons")
    public List<VoiceMeeterParam> getAdvancedButtonParams() {
        return groups(true);
    }

    private List<VoiceMeeterParam> groups(boolean buttons) {
        if (!voicemeeter.login()) {
            return List.of();
        }
        return groups(voicemeeter.getVersion(), buttons, (ct, idx) -> voicemeeter.readString(voicemeeter.makeParameterString(ct, idx, "Label")));
    }

    static List<VoiceMeeterParam> groups(@Nullable VoicemeeterVersion version, boolean buttons, BiFunction<ControlType, Integer, String> labelOf) {
        if (version == null) {
            return List.of();
        }
        var result = new ArrayList<VoiceMeeterParam>();
        for (var ct : ControlType.values()) {
            var paramNames = paramNames(version, ct, buttons);
            for (var idx = 0; idx < paramNames.size(); idx++) {
                var prefix = ct.getName() + "[" + idx + "].";
                var params = StreamEx.of(paramNames.get(idx)).map(prefix::concat).toList();
                var label = labelOf.apply(ct, idx);
                var name = ct + " " + (idx + 1) + (StringUtils.isBlank(label) ? "" : " · " + label.trim());
                result.add(new VoiceMeeterParam(name, params));
            }
        }
        return result;
    }

    /** Per strip or bus index, the parameter names the given Voicemeeter version offers. */
    private static List<List<String>> paramNames(VoicemeeterVersion version, ControlType ct, boolean buttons) {
        if (buttons) {
            var perIndex = ct == ControlType.STRIP ? version.getStripButtons() : version.getBusButtons();
            return StreamEx.of(perIndex).map(types -> StreamEx.of(types).map(Voicemeeter.ButtonType::getParameterName).toList()).toList();
        }
        var perIndex = ct == ControlType.STRIP ? version.getStripDials() : version.getBusDials();
        return StreamEx.of(perIndex).map(types -> StreamEx.of(types).map(Voicemeeter.DialType::getParameterName).toList()).toList();
    }
}
