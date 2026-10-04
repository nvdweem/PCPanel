package com.getpcpanel.integration.volume.platform.linux;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.getpcpanel.alerts.MicUsage;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PulseAudioTarget;
import com.getpcpanel.platform.LinuxBuild;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The apps recording from an input right now, from {@code pactl list source-outputs}: every recording stream except
 * those on a monitor (which record what an output plays, not a microphone) and PCPanel's own level meters.
 */
@Log4j2
@LinuxBuild
@ApplicationScoped
class LinuxMicUsage implements MicUsage {
    @Inject PulseAudioWrapper cmd;

    @Override
    public Set<String> appsUsingMic() {
        try {
            return appsUsingMic(cmd.getRecordings(), cmd.getSources());
        } catch (RuntimeException e) {
            log.debug("Unable to read microphone usage", e);
            return Set.of();
        }
    }

    static Set<String> appsUsingMic(Collection<PulseAudioTarget> recordings, Collection<PulseAudioTarget> sources) {
        var monitors = new HashSet<Integer>();
        for (var source : sources) {
            if (StringUtils.endsWith(source.name(), ".monitor") || "monitor".equals(source.properties().get("device.class"))) {
                monitors.add(source.index());
            }
        }
        var result = new HashSet<String>();
        for (var r : recordings) {
            var props = r.properties();
            if (LinuxAudioLevelMeter.CLIENT_NAME.equals(props.get("application.name"))
                    || monitors.contains(NumberUtils.toInt(r.metas().get("Source"), -1))) {
                continue;
            }
            var binary = props.get("application.process.binary");
            var name = StringUtils.isNotBlank(binary) ? binary : StringUtils.firstNonBlank(props.get("pipewire.access.portal.app_id"), props.get("application.name"));
            if (StringUtils.isNotBlank(name)) {
                result.add(name.toLowerCase());
            }
        }
        return result;
    }
}
