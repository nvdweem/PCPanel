package com.getpcpanel.integration.volume.platform.linux;

import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.getpcpanel.integration.volume.AppOutputRouter;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.InOutput;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PactlTimeoutException;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PulseAudioTarget;
import com.getpcpanel.platform.LinuxBuild;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

/**
 * Moves the apps' streams to a sink with {@code pactl move-sink-input}. A device id is a sink's name (inputs carry
 * the {@link SndCtrlPulseAudio#INPUT_PREFIX} and are no sink, so they are refused). A stream names its app by its
 * binary, its application name or its portal app id, as for App volume.
 */
@Log4j2
@Unremovable
@LinuxBuild
@ApplicationScoped
class LinuxAppOutputRouter implements AppOutputRouter {
    @Inject PulseAudioWrapper pactl;

    @Override
    public @Nullable String route(Set<String> exeNames, @Nullable String deviceId) {
        try {
            var sinkName = deviceId == null ? pactl.defaultDeviceNames().get(InOutput.output) : deviceId;
            var sink = StreamEx.of(pactl.execAndParse(InOutput.output)).findFirst(s -> s.name() != null && s.name().equals(sinkName)).orElse(null);
            if (sink == null) {
                log.warn("Cannot send {} to {}: no such output", exeNames, deviceId == null ? "the default output" : deviceId);
                return null;
            }
            var streams = StreamEx.of(pactl.getSessions()).filter(s -> namesApp(exeNames, s)).toList();
            if (streams.isEmpty()) {
                return null;
            }
            streams.forEach(s -> pactl.moveSession(s.index(), sink.name()));
            return StringUtils.firstNonBlank(sink.metas().get("Description"), sink.name());
        } catch (PactlTimeoutException e) {
            log.warn("{}; the apps were not moved", e.getMessage());
            return null;
        }
    }

    private static boolean namesApp(Set<String> exeNames, PulseAudioTarget stream) {
        var props = stream.properties();
        return StreamEx.of(props.get("application.process.binary"), props.get("application.name"), props.get("pipewire.access.portal.app_id"))
                       .anyMatch(key -> AppOutputRouter.namesApp(exeNames, key));
    }
}
