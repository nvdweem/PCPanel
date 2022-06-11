package com.getpcpanel.cpp.linux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.getpcpanel.spring.ConditionalOnLinux;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Component
@ConditionalOnLinux
@RequiredArgsConstructor
public class PulseAudioEventListener extends Thread {
    private final ApplicationEventPublisher eventPublisher;
    private boolean running = true;

    @PostConstruct
    public void init() {
        setName("PulseAudio change listener");
        setDaemon(true);
        start();
    }

    @PreDestroy
    public void deInit() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                var process = new ProcessBuilder("pactl", "subscribe").start();
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                //noinspection NestedAssignment
                while ((line = reader.readLine()) != null) {
                    checkTrigger(line);
                }
            } catch (IOException e) {
                log.warn("Subscribe process error", e);
            }
        }
    }

    private void checkTrigger(String line) {
        if (StringUtils.containsIgnoreCase(line, "Event 'new' on sink-input") || StringUtils.containsIgnoreCase(line, "Event 'remove' on sink-input")) {
            eventPublisher.publishEvent(new LinuxSessionChangedEvent());
        }
        if (StringUtils.containsIgnoreCase(line, "Event 'new' on sink") || StringUtils.containsIgnoreCase(line, "Event 'remove' on sink")) {
            eventPublisher.publishEvent(new LinuxDeviceChangedEvent());
        }
    }

    public static class LinuxDeviceChangedEvent {
    }

    public static class LinuxSessionChangedEvent {
    }
}
