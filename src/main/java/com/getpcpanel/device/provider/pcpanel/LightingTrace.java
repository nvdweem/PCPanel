package com.getpcpanel.device.provider.pcpanel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.getpcpanel.sleepdetection.SystemEvent;
import com.getpcpanel.util.io.FileUtil;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.log4j.Log4j2;

/**
 * The lighting trace: an opt-in record, in a log file of its own ({@link #file()}), of every report the app hands to
 * a PCPanel and of what became of it, plus the events around it (connects, disconnects, system events and the user's
 * own marks). It exists to tell apart "the app told the panel to go dark" from "the panel went dark by itself": each
 * lighting state is classified from its bytes ({@link LightingFrames}) and logged with the code that sent it.
 *
 * <p>Off by default. Switched on, it stays on across restarts (a marker file in the data directory) until switched
 * off. Nothing is built while it is off beyond one volatile read. A state identical to the one before it from the
 * same caller is counted rather than logged again, so a steady panel writes nothing and the file is bounded by
 * rotation. Serial numbers appear masked to their last four characters.
 */
@Log4j2
@ApplicationScoped
public class LightingTrace {
    static final String TOPIC = "pcpanel.lighting-trace";
    static final String MARKER_FILE = "lighting-trace.on";
    private static final int SERIAL_SHOWN = 4;
    private static final int ORIGIN_FRAMES = 3;
    private static final String APP_PACKAGE = "com.getpcpanel.";
    /** Suffixes of the classes Quarkus Arc generates around a bean. */
    private static final List<String> GENERATED_SUFFIXES = List.of("_ClientProxy", "_Subclass");
    /** The plumbing between a caller and the device; the trace names the code that called into it instead. */
    private static final Set<String> PLUMBING = Set.of(
            LightingTrace.class.getName(),
            DeviceCommunicationHandler.class.getName(),
            OutputInterpreter.class.getName(),
            HidOutputQueue.class.getName());

    @Inject
    FileUtil fileUtil;
    @ConfigProperty(name = "quarkus.log.handler.file.\"lighting-trace\".path")
    String file;
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    private final AtomicLong sequence = new AtomicLong();
    /** Per device and direction, the last state logged and how often it repeated since. */
    private final Map<String, Repeat> repeats = new HashMap<>();
    private volatile boolean enabled;
    private Path marker;
    private Consumer<String> sink;

    LightingTrace() {
    }

    /** For tests: the trace writes its lines to {@code sink} and keeps its switch in {@code marker}. */
    LightingTrace(Path marker, Consumer<String> sink) {
        this.marker = marker;
        this.sink = sink;
        enabled = Files.exists(marker);
    }

    @PostConstruct
    void init() {
        marker = fileUtil.getFile(MARKER_FILE).toPath();
        Logger traceLog = LogManager.getLogger(TOPIC);
        sink = traceLog::info;
        enabled = Files.exists(marker);
    }

    void onStartup(@Observes StartupEvent event) {
        if (enabled) {
            line("trace on: app started (pcpanel " + version + ")");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** The trace file; its rotations sit beside it with {@code .1}, {@code .2}, ... appended. */
    public String file() {
        return file;
    }

    public synchronized void setEnabled(boolean on) {
        if (on == enabled) {
            return;
        }
        try {
            if (on) {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, "");
            } else {
                Files.deleteIfExists(marker);
            }
        } catch (IOException e) {
            log.warn("Unable to {} the lighting trace switch {}", on ? "write" : "remove", marker, e);
        }
        if (on) {
            enabled = true;
            repeats.clear();
            line("trace on (pcpanel " + version + ")");
        } else {
            line("trace off");
            enabled = false;
        }
    }

    /** A line the user adds at the moment they notice something, such as the lights going out. */
    public synchronized void mark(String note) {
        if (!enabled) {
            return;
        }
        // Repeats are only counted out when a different state arrives; settle them now so the lines before the mark
        // say everything that was sent up to it.
        repeats.forEach((key, last) -> {
            if (last.count > 0) {
                repeatedLine(key, last.count);
                last.count = 0;
            }
        });
        line("MARK " + note);
    }

    /** Reports handed to a device: a lighting state ({@code lighting}) or a message such as the init. */
    void requested(String serial, String kind, byte[][] reports) {
        if (!enabled) {
            return;
        }
        var fingerprint = LightingFrames.fingerprint(reports);
        var origin = origin();
        var darkness = "lighting".equals(kind) ? " " + LightingFrames.classify(reports) : "";
        var repeatKey = mask(serial) + " requested " + kind;
        synchronized (this) {
            if (repeated(repeatKey, fingerprint + origin)) {
                return;
            }
            line(mask(serial) + " " + kind + darkness + " fp=" + fingerprint + " from " + origin
                    + " [" + Thread.currentThread().getName() + "] " + LightingFrames.hex(reports));
        }
    }

    /**
     * Reports written to a device; {@code results} holds hidapi's answer per report (negative is a failure) and
     * {@code error} hidapi's last error when one failed.
     */
    void written(String serial, byte[][] reports, int[] results, @Nullable String error) {
        if (!enabled) {
            return;
        }
        var fingerprint = LightingFrames.fingerprint(reports);
        var failed = -1;
        for (var i = 0; i < results.length; i++) {
            if (results[i] < 0) {
                failed = i;
                break;
            }
        }
        var repeatKey = mask(serial) + " written";
        synchronized (this) {
            if (failed < 0) {
                if (!repeated(repeatKey, fingerprint)) {
                    line(mask(serial) + " wrote fp=" + fingerprint + " ok");
                }
                return;
            }
            repeats.remove(repeatKey); // a failure always shows, and the next success after it too
            line(mask(serial) + " wrote fp=" + fingerprint + " FAILED at report " + (failed + 1) + "/" + results.length
                    + " result=" + results[failed] + ": " + error);
        }
    }

    /** A change in a device's connection: found, opened, lost, a read error. */
    public void device(String serial, String what) {
        if (enabled) {
            line(mask(serial) + " " + what);
        }
    }

    void onSystemEvent(@Observes SystemEvent event) {
        if (enabled) {
            line("system event: " + event.type());
        }
    }

    private boolean repeated(String key, String state) {
        var last = repeats.get(key);
        if (last != null && last.state.equals(state)) {
            last.count++;
            return true;
        }
        if (last != null && last.count > 0) {
            repeatedLine(key, last.count);
        }
        repeats.put(key, new Repeat(state));
        return false;
    }

    private void repeatedLine(String key, int count) {
        line("  (" + key + ": the previous state " + count + " more time" + (count == 1 ? "" : "s") + ")");
    }

    private void line(String text) {
        sink.accept("#" + sequence.incrementAndGet() + " " + text);
    }

    /** The app code that asked for this write, nearest first, past the device plumbing. */
    static String origin() {
        var frames = StackWalker.getInstance().walk(stack -> stack
                .filter(f -> f.getClassName().startsWith(APP_PACKAGE))
                .filter(f -> !PLUMBING.contains(outerClass(f.getClassName())))
                .limit(ORIGIN_FRAMES)
                .map(f -> simpleName(f.getClassName()) + "." + f.getMethodName())
                .collect(Collectors.joining(" < ")));
        return frames.isEmpty() ? "?" : frames;
    }

    /**
     * The source class a stack frame belongs to: a nested class or lambda as its outer class, and a class Quarkus
     * generates around a bean (its client proxy, its interceptor subclass) as the bean's own class.
     */
    static String outerClass(String className) {
        var nested = className.indexOf('$');
        var outer = nested < 0 ? className : className.substring(0, nested);
        for (var generated : GENERATED_SUFFIXES) {
            if (outer.endsWith(generated)) {
                return outer.substring(0, outer.length() - generated.length());
            }
        }
        return outer;
    }

    private static String simpleName(String className) {
        var outer = outerClass(className);
        var simple = outer.substring(outer.lastIndexOf('.') + 1);
        return className.contains("$$Lambda") ? simple + "(lambda)" : simple;
    }

    static String mask(String serial) {
        if (serial == null || serial.length() <= SERIAL_SHOWN) {
            return "panel " + serial;
        }
        return "panel *" + serial.substring(serial.length() - SERIAL_SHOWN);
    }

    private static final class Repeat {
        private final String state;
        private int count;

        private Repeat(String state) {
            this.state = state;
        }
    }
}
