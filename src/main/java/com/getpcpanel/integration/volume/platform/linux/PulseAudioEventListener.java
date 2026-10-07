package com.getpcpanel.integration.volume.platform.linux;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.annotation.Nullable;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.os.ProcessHelper;

import dev.niels.pulse.PulseClient;
import dev.niels.pulse.PulseException;
import dev.niels.pulse.model.SubscriptionEvent;
import dev.niels.pulse.model.SubscriptionEvent.Facility;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import io.quarkus.runtime.Startup;
import lombok.extern.log4j.Log4j2;

/**
 * Follows the server's change events and turns them into the device/session events the rest of the Linux backend
 * reacts to: over the protocol connection's subscription while there is one ({@link PulseConnection}), otherwise from
 * {@code pactl subscribe}. Both are read as pactl's event lines, so {@link #checkTrigger} serves both.
 */
@Log4j2
@Startup
@Singleton
@LinuxBuild
class PulseAudioEventListener {
    private static final Set<Facility> FACILITIES = Set.of(Facility.SINK, Facility.SOURCE, Facility.SINK_INPUT, Facility.SOURCE_OUTPUT, Facility.SERVER);

    @Inject
    Event<Object> eventBus;
    @Inject
    ProcessHelper processHelper;
    @Inject
    PulseConnection pulse;
    private final CircularFifoQueue<String> latestEvents = new CircularFifoQueue<>(50);
    private final Pattern numberPattern = Pattern.compile("#(\\d+)");

    private volatile boolean running = true;
    private Thread thread;

    @PostConstruct
    public void init() {
        thread = new Thread(this::run, "PulseAudio change listener");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public void deInit() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /**
     * How long to wait before restarting the stream. Without it, a {@code pactl} that exits immediately —
     * missing on the host, or unreachable through the Flatpak's {@code flatpak-spawn} shim — turns this into
     * a process-spawning hot loop that burns a core and floods nothing but the log.
     */
    private static final long RESTART_DELAY_MS = 5000;

    /**
     * Health of the change stream, for the bug-report bundle. A dead or never-started stream is invisible
     * from the outside and presents as an application picker frozen on whatever was playing at startup —
     * the second half of #151 — so the report has to be able to say which it was.
     */
    private volatile Instant streamStartedAt;
    private volatile Instant lastEventAt;
    private volatile String lastEnded;
    private volatile String source = "none";
    private final AtomicInteger restarts = new AtomicInteger();

    String healthSummary() {
        var started = streamStartedAt;
        if (started == null) {
            return "never started" + (lastEnded == null ? "" : " (" + lastEnded + ")");
        }
        return source + ", running since " + started
                + ", last event " + (lastEventAt == null ? "none yet" : lastEventAt)
                + ", restarts " + restarts.get()
                + (lastEnded == null ? "" : ", last ended: " + lastEnded);
    }

    private void run() {
        while (running) {
            var client = pulse.client();
            if (client != null) {
                followProtocol(client);
                continue;
            }
            try {
                streamStartedAt = Instant.now();
                source = "pactl subscribe";
                var exit = processHelper.stream(ProcessHelper.PARSEABLE_OUTPUT, this::onLine, "pactl", "subscribe");
                // The stream ended. Until it is back, nothing updates the device/session lists from the OS,
                // which shows up as an application picker frozen on whatever was playing at startup — so say
                // so rather than restarting in silence (#151).
                streamStartedAt = null;
                lastEnded = "exit " + exit + " at " + Instant.now();
                log.warn("'pactl subscribe' ended (exit {}); audio device/session changes are not being observed. "
                        + "Retrying in {}ms.", exit, RESTART_DELAY_MS);
            } catch (IOException e) {
                streamStartedAt = null;
                lastEnded = "could not be started: " + e;
                log.warn("Subscribe process error", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            restarts.incrementAndGet();
            sleepBeforeRestart();
        }
    }

    /**
     * Follows the connection's subscription until the connection is gone. Changes made while a previous connection
     * was down are not replayed, so a resubscription asks for a full re-read; the first one needn't, the backend reads
     * everything when it starts.
     */
    private void followProtocol(PulseClient client) {
        try {
            client.subscribe(FACILITIES, this::onEvent);
            streamStartedAt = Instant.now();
            source = "protocol subscription";
            if (restarts.get() > 0) {
                eventBus.fire(new LinuxDeviceChangedEvent());
                eventBus.fire(new LinuxSessionChangedEvent(null));
            }
            var reason = client.closed().get();
            lastEnded = "connection closed at " + Instant.now() + " (" + reason + ")";
            log.warn("PulseAudio protocol connection closed; audio device/session changes are not being observed until it is back: {}", reason.toString());
        } catch (PulseException e) {
            lastEnded = "subscribe failed: " + e.getMessage();
            pulse.lost(client, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
            return;
        } catch (ExecutionException e) {
            lastEnded = "connection failed: " + e.getCause();
        }
        streamStartedAt = null;
        restarts.incrementAndGet();
        sleepBeforeRestart();
    }

    private void onEvent(SubscriptionEvent event) {
        onLine("Event '" + event.type().pactlName() + "' on " + event.facility().pactlName() + " #" + event.index());
    }

    private void onLine(String line) {
        lastEventAt = Instant.now();
        latestEvents.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " - " + line);
        checkTrigger(line);
    }

    private void sleepBeforeRestart() {
        try {
            Thread.sleep(RESTART_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    String getDebugOutput() {
        return "change events (" + source + "):\n" + String.join("\n", latestEvents);
    }

    void checkTrigger(String line) {
        if (StringUtils.containsAnyIgnoreCase(line
                , "Event 'new' on sink-input"
                , "Event 'remove' on sink-input"
                , "Event 'change' on sink-input")) {
            var m = numberPattern.matcher(line);
            eventBus.fire(new LinuxSessionChangedEvent(m.find() ? NumberUtils.toInt(m.group(1)) : null));
        }
        // A source is matched with its '#' so recording streams ("source-output") don't count as devices. The server
        // reports a change when its default sink or source changes.
        if (StringUtils.containsAnyIgnoreCase(line
                , "Event 'new' on sink"
                , "Event 'remove' on sink"
                , "Event 'new' on source #"
                , "Event 'remove' on source #"
                , "Event 'change' on server")) {
            eventBus.fire(new LinuxDeviceChangedEvent());
        }
        // A volume or mute change on an output or input; kept apart from the list changes above because a turning dial
        // reports one per write, so the receiver coalesces them.
        if (StringUtils.containsAnyIgnoreCase(line, "Event 'change' on sink #", "Event 'change' on source #")) {
            eventBus.fire(new LinuxDeviceStateChangedEvent());
        }
    }

    public static class LinuxDeviceChangedEvent {
    }

    public static class LinuxDeviceStateChangedEvent {
    }

    public record LinuxSessionChangedEvent(@Nullable Integer sessionId) {
    }
}
