package com.getpcpanel.integration.volume.platform.linux;

import java.io.File;

import com.getpcpanel.util.os.ProcessHelper;

/**
 * Run on Linux against a live server while a tone plays on a sink: meters that sink, one stream on it and the default
 * output, and records the sink for the visualizer, once over the protocol and once through parec, and prints what each
 * measured. Takes the sink's name and the index of a stream playing on it.
 */
public final class LinuxRecordingCheck {
    private LinuxRecordingCheck() {
    }

    public static void main(String[] args) throws Exception {
        var sink = args[0];
        var stream = Integer.parseInt(args[1]);
        for (var useProtocol : new boolean[] { true, false }) {
            var recorder = recorder(useProtocol);
            System.out.println(useProtocol ? "== protocol (" + recorder.pulse.state() + ")" : "== parec");

            var meter = new LinuxAudioLevelMeter();
            meter.recorder = recorder;
            var session = new PulseAudioAudioSession(null, stream, -1, new File("/"), "tone", "", 1, false, null);
            for (var i = 0; i < 4; i++) { // the first asks start the recordings
                meter.sample().device(sink);
                meter.sample().session(session);
                meter.sample().defaultOutput();
                Thread.sleep(250);
            }
            var levels = meter.sample();
            System.out.printf("meter: %s %.3f, stream %d %.3f, default output %.3f%n", sink, levels.device(sink), stream, levels.session(session),
                    levels.defaultOutput());
            meter.stop();

            var capture = new LinuxLoopbackCapture();
            capture.recorder = recorder;
            capture.sndCtrl = new SndCtrlPulseAudio(); // no devices known: the capture follows nothing
            var started = capture.start(sink, false);
            Thread.sleep(200);
            capture.read(new float[LinuxLoopbackCapture.RATE]); // drop what came in while it started
            Thread.sleep(1000);
            var into = new float[LinuxLoopbackCapture.RATE * 2];
            var n = capture.read(into);
            var max = 0f;
            for (var i = 0; i < n; i++) {
                max = Math.max(max, Math.abs(into[i]));
            }
            System.out.printf("visualizer: started %s, %d samples in 1 s (expect about %d), peak %.3f%n", started, n, LinuxLoopbackCapture.RATE, max);
            capture.stop();
            if (recorder.pulse.client() != null) {
                recorder.pulse.close();
            }
        }
        System.exit(0);
    }

    private static LinuxRecorder recorder(boolean useProtocol) {
        var recorder = new LinuxRecorder();
        recorder.processes = new ProcessHelper();
        recorder.pulse = new PulseConnection();
        recorder.pulse.enabled = useProtocol;
        return recorder;
    }
}
