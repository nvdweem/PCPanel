package com.getpcpanel.integration.volume.platform.linux;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.InOutput;
import com.getpcpanel.integration.volume.platform.linux.PulseAudioWrapper.PulseAudioTarget;
import com.getpcpanel.util.os.ProcessHelper;

/**
 * Run on Linux against a live server: reads everything once over the protocol and once through pactl and reports any
 * difference the Linux backend would see, then applies each kind of write over the protocol and reads it back through
 * pactl. Takes the name of a sink it may change (a null sink), optionally the index of a stream playing on it, and the
 * name of a second sink to move that stream to.
 * {@code -Dparity.default=false} skips switching the default sink: on WSLg that can deadlock PulseAudio's RDP sink
 * whoever asks for it, pactl included.
 */
public final class PulseParityCheck {
    private static final List<String> METAS = List.of("Name", "Description", "Mute", "Sink", "Source", "Corked");
    private static final List<String> PROPERTIES = List.of("application.name", "application.process.binary", "application.process.id",
            "media.name", "device.class", "pipewire.access.portal.app_id");
    private static int differences;

    private PulseParityCheck() {
    }

    public static void main(String[] args) {
        var sinkName = args[0];
        var nativeWrapper = wrapper(true);
        var pactlWrapper = wrapper(false);
        var sut = new SndCtrlPulseAudio();

        compare("devices", nativeWrapper.devices(), pactlWrapper.devices(), sut);
        for (var type : InOutput.values()) {
            compare(type.name(), nativeWrapper.execAndParse(type), pactlWrapper.execAndParse(type), sut);
        }
        check("default names", nativeWrapper.defaultDeviceNames(), pactlWrapper.defaultDeviceNames());

        var sink = pactlWrapper.execAndParse(InOutput.output).stream().filter(t -> sinkName.equals(t.name())).findFirst().orElseThrow();
        nativeWrapper.setDeviceVolume(true, sink.index(), 0.25f);
        check("sink volume written", 0.25f, sut.extractVolume(reread(pactlWrapper, InOutput.output, sink.index())));
        nativeWrapper.muteDevice(true, sink.index(), MuteType.toggle);
        var toggled = SndCtrlPulseAudio.isMuted(reread(pactlWrapper, InOutput.output, sink.index()));
        check("sink mute toggled", !SndCtrlPulseAudio.isMuted(sink), toggled);
        nativeWrapper.muteDevice(true, sink.index(), MuteType.unmute);
        check("sink unmuted", false, SndCtrlPulseAudio.isMuted(reread(pactlWrapper, InOutput.output, sink.index())));

        var defaults = pactlWrapper.defaultDeviceNames();
        var previous = pactlWrapper.execAndParse(InOutput.output).stream().filter(t -> t.name().equals(defaults.get(InOutput.output))).findFirst().orElseThrow();
        if (Boolean.parseBoolean(System.getProperty("parity.default", "true"))) {
            switchDefault(nativeWrapper, pactlWrapper, sink, sinkName, previous, defaults.get(InOutput.output));
        }


        if (args.length > 1) {
            var stream = Integer.parseInt(args[1]);
            nativeWrapper.setSessionVolume(stream, 0.5f);
            check("stream volume written", 0.5f, sut.extractVolume(reread(pactlWrapper, InOutput.session, stream)));
            nativeWrapper.muteSession(stream, MuteType.toggle);
            check("stream mute toggled", true, SndCtrlPulseAudio.isMuted(reread(pactlWrapper, InOutput.session, stream)));
            nativeWrapper.muteSession(stream, MuteType.unmute);
        }
        if (args.length > 2) {
            var stream = Integer.parseInt(args[1]);
            var other = pactlWrapper.execAndParse(InOutput.output).stream().filter(t -> args[2].equals(t.name())).findFirst().orElseThrow();
            nativeWrapper.moveSession(stream, other.name());
            check("stream moved", String.valueOf(other.index()), reread(pactlWrapper, InOutput.session, stream).metas().get("Sink"));
            nativeWrapper.moveSession(stream, sinkName);
        }
        System.out.println(differences == 0 ? "PARITY OK" : differences + " DIFFERENCES");
        System.exit(differences == 0 ? 0 : 1);
    }

    private static void switchDefault(PulseAudioWrapper nativeWrapper, PulseAudioWrapper pactlWrapper, PulseAudioTarget sink, String sinkName,
            PulseAudioTarget previous, String previousName) {
        nativeWrapper.setDefaultDevice(true, sink.index());
        check("default sink set", sinkName, pactlWrapper.defaultDeviceNames().get(InOutput.output));
        nativeWrapper.setDefaultDevice(true, previous.index());
        check("default sink restored", previousName, pactlWrapper.defaultDeviceNames().get(InOutput.output));
    }

    private static PulseAudioWrapper wrapper(boolean useNative) {
        var wrapper = new PulseAudioWrapper();
        wrapper.processHelper = new ProcessHelper();
        wrapper.timeoutMillis = 15_000;
        if (useNative) {
            wrapper.pulse = new PulseConnection();
            wrapper.pulse.enabled = true;
            Objects.requireNonNull(wrapper.pulse.client(), () -> "no protocol connection: " + wrapper.pulse.state());
            System.out.println("protocol: " + wrapper.pulse.state());
        }
        return wrapper;
    }

    private static PulseAudioTarget reread(PulseAudioWrapper wrapper, InOutput type, int index) {
        return wrapper.execAndParse(type).stream().filter(t -> t.index() == index).findFirst().orElseThrow();
    }

    private static void compare(String what, List<PulseAudioTarget> viaNative, List<PulseAudioTarget> viaPactl, SndCtrlPulseAudio sut) {
        check(what + " count", viaPactl.size(), viaNative.size());
        var a = viaNative.stream().sorted(Comparator.comparing(PulseAudioTarget::type).thenComparing(PulseAudioTarget::index)).toList();
        var b = viaPactl.stream().sorted(Comparator.comparing(PulseAudioTarget::type).thenComparing(PulseAudioTarget::index)).toList();
        for (var i = 0; i < Math.min(a.size(), b.size()); i++) {
            var n = a.get(i);
            var p = b.get(i);
            var label = what + " " + p.type() + " #" + p.index();
            check(label + " index", p.index(), n.index());
            check(label + " default", p.isDefault(), n.isDefault());
            check(label + " volume", sut.extractVolume(p), sut.extractVolume(n));
            METAS.forEach(key -> check(label + " " + key, p.metas().get(key), n.metas().get(key)));
            PROPERTIES.forEach(key -> check(label + " " + key, p.properties().get(key), n.properties().get(key)));
        }
        System.out.println(what + ": " + viaNative.size() + " compared");
    }

    private static void check(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            differences++;
            System.out.println("DIFF " + what + ": pactl=" + expected + " protocol=" + actual);
        }
    }
}
