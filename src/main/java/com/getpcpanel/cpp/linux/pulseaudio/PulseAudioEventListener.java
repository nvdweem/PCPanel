package com.getpcpanel.cpp.linux.pulseaudio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.annotation.Nullable;

import com.getpcpanel.platform.LinuxBuild;
import com.getpcpanel.util.ProcessHelper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import io.quarkus.runtime.Startup;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Startup
@Singleton
@LinuxBuild
public class PulseAudioEventListener {
    @Inject
    Event<Object> eventBus;
    @Inject
    ProcessHelper processHelper;
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

    private void run() {
        while (running) {
            try {
                var process = processHelper.builder("pactl", "subscribe").start();
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                var dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String line;
                //noinspection NestedAssignment
                while ((line = reader.readLine()) != null) {
                    latestEvents.add(dateFormat.format(new Date()) + " - " + line);
                    checkTrigger(line);
                }
            } catch (IOException e) {
                log.warn("Subscribe process error", e);
            }
        }
    }

    String getDebugOutput() {
        return "pactl subscribe:\n" + String.join("\n", latestEvents);
    }

    private void checkTrigger(String line) {
        if (StringUtils.containsAnyIgnoreCase(line
                , "Event 'new' on sink-input"
                , "Event 'remove' on sink-input"
                , "Event 'change' on sink-input")) {
            var m = numberPattern.matcher(line);
            eventBus.fire(new LinuxSessionChangedEvent(m.find() ? NumberUtils.toInt(m.group(1)) : null));
        }
        if (StringUtils.containsAnyIgnoreCase(line, "Event 'new' on sink", "Event 'remove' on sink")) {
            eventBus.fire(new LinuxDeviceChangedEvent());
        }
    }

    public static class LinuxDeviceChangedEvent {
    }

    public record LinuxSessionChangedEvent(@Nullable Integer sessionId) {
    }
}
