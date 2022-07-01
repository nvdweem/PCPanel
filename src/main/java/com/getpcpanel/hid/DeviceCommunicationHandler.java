package com.getpcpanel.hid;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.tuple.Pair;
import org.hid4java.HidDevice;
import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.profile.SaveService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeviceCommunicationHandler extends Thread {
    private static final byte INPUT_CODE_KNOB_CHANGE = 1;
    private static final byte INPUT_CODE_BUTTON_CHANGE = 2;

    private final ApplicationEventPublisher eventPublisher;
    private final DeviceScanner deviceScanner;
    private final SaveService saveService;
    private final String key;
    private final HidDevice device;

    private static final int READ_TIMEOUT_MILLIS = 100;

    private static final int PACKET_LENGTH = 64;
    private static final int FIRST_NON_INITIAL_READS = 20; // Could have been 9 (dials/sliders of the pro) but take 20 to be safe
    private int readUntilNotInitial = FIRST_NON_INITIAL_READS;

    private final ConcurrentLinkedQueue<byte[]> priorityQueue = new ConcurrentLinkedQueue<>();
    private final AtomicReference<byte[][]> mostRecentRGBRequest = new AtomicReference<>();
    private final KnobDebouncer debouncer = new KnobDebouncer();

    public DeviceCommunicationHandler(DeviceScanner deviceScanner, ApplicationEventPublisher eventPublisher, SaveService saveService, String key, HidDevice device) {
        setName("HIDHandler");
        setDaemon(true);
        this.eventPublisher = eventPublisher;
        this.deviceScanner = deviceScanner;
        this.saveService = saveService;
        this.key = key;
        this.device = device;
    }

    public void addToPriorityQueue(byte[]... datas) {
        Collections.addAll(priorityQueue, datas);
    }

    public void publishRGBUpdate(byte[]... data) {
        mostRecentRGBRequest.set(data);
    }

    public void sendMessage(boolean priority, byte[]... data) {
        if (priority) {
            addToPriorityQueue(data);
        } else {
            publishRGBUpdate(data);
        }
    }

    @Override
    public void run() {
        while (deviceScanner.getConnectedDevice(key) == this) {
            var moreData = true;
            while (moreData) {
                var data = new byte[PACKET_LENGTH];
                var val = device.read(data, READ_TIMEOUT_MILLIS);
                if (readUntilNotInitial != 0) {
                    readUntilNotInitial--;
                }

                switch (val) {
                    case -1 -> {
                        log.error("DCH ERR: {}", device.getLastErrorMessage());
                        deviceScanner.deviceRemoved(key, device);
                        return;
                    }
                    case 0 -> {
                        moreData = false;
                        continue;
                    }
                }
                if (log.isDebugEnabled()) {
                    log.debug("< {}", Arrays.toString(data));
                }
                interpretInputData(readUntilNotInitial != 0, data);
            }
            if (!priorityQueue.isEmpty()) {
                sendMessageReal(priorityQueue.remove());
                continue;
            }
            if (mostRecentRGBRequest.get() != null) {
                for (var data : mostRecentRGBRequest.getAndSet(null)) {
                    sendMessageReal(data);
                }
            }
        }
        debouncer.shutdown();
    }

    private void sendMessageReal(byte[] info) {
        if (info.length > PACKET_LENGTH)
            throw new IllegalArgumentException("info cannot be greater than packet_length");
        var message = new byte[PACKET_LENGTH];
        System.arraycopy(info, 0, message, 0, info.length);
        var val = device.write(message, PACKET_LENGTH, (byte) 0);
        if (val >= 0) {
            if (log.isTraceEnabled()) {
                log.trace("> {}: {}", val, Arrays.toString(info));
            }
        } else {
            log.error("{} {}     {}", device.getLastErrorMessage(), val, Arrays.toString(info), new Exception().fillInStackTrace());
        }
    }

    private void interpretInputData(boolean initial, byte[] data) {
        if (data[0] == INPUT_CODE_KNOB_CHANGE) {
            var knob = data[1] & 0xFF;
            var value = data[2] & 0xFF;
            try {
                triggerOrDebounce(new KnobRotateEvent(key, knob, value, initial));
            } catch (Exception ex) {
                log.error("Unable to handle knob rotate", ex);
            }
        } else if (data[0] == INPUT_CODE_BUTTON_CHANGE) {
            var knob = data[1] & 0xFF;
            var value = data[2] & 0xFF;
            try {
                eventPublisher.publishEvent(new ButtonPressEvent(key, knob, value == 1));
            } catch (Exception ex) {
                log.error("Unable to handle button press", ex);
            }
        } else {
            log.error("Invalid Input in DeviceInputListener: {}", Arrays.toString(data));
        }
    }

    private void triggerOrDebounce(KnobRotateEvent event) {
        var delay = saveService.get().getPreventSliderTwitchDelay();
        if (delay == null || delay == 0) {
            eventPublisher.publishEvent(event);
        } else {
            debouncer.debounce(event, delay);
        }
    }

    public Queue<byte[]> getPriorityQueue() {
        return priorityQueue;
    }

    public record KnobRotateEvent(String serialNum, int knob, int value, boolean initial) {
    }

    public record ButtonPressEvent(String serialNum, int button, boolean pressed) {
    }

    private class KnobDebouncer {
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        private final Map<Integer, Pair<Deque<KnobRotateEvent>, Future<?>>> delayedMap = new ConcurrentHashMap<>();

        public void debounce(KnobRotateEvent event, long delay) {
            var prev = cancel(event.knob());
            prev.add(event);
            if (prev.size() == 1) {
                // Initial event, just send
                eventPublisher.publishEvent(event);
                delayedMap.put(event.knob(), Pair.of(new ArrayDeque<>(List.of(event)), null));
            } else if (prev.size() == 2) {
                // Any first event after initial or after debounce, delay this
                schedule(prev, event, delay);
                log.trace("Initial: {}", prev);
            } else if (prev.size() == 3) {
                // Second actual event after debounce, see if we are twitching
                var prevEvent = prev.removeFirst();
                if (prevEvent.value() != event.value()) { // We are not twitching
                    eventPublisher.publishEvent(prevEvent); // Trigger the previous
                    schedule(prev, event, delay); // Delay the current
                    log.trace("Trigger and go: {}", prev);
                } else {
                    prev.removeFirst(); // Remove the twitch
                    delayedMap.put(event.knob(), Pair.of(prev, null));
                    log.debug("Twitch cancelled: {}", prev);
                }
            }
        }

        private Deque<KnobRotateEvent> cancel(int knob) {
            var prev = delayedMap.remove(knob);
            if (prev == null) {
                return new ArrayDeque<>();
            } else {
                if (prev.getRight() != null) {
                    prev.getRight().cancel(true);
                }
                return prev.getLeft();
            }
        }

        private void schedule(Deque<KnobRotateEvent> prevs, KnobRotateEvent event, long delay) {
            delayedMap.put(event.knob, Pair.of(prevs, scheduler.schedule(() -> {
                try {
                    eventPublisher.publishEvent(event);
                } finally {
                    delayedMap.put(event.knob(), Pair.of(new ArrayDeque<>(List.of(event)), null));
                }
            }, delay, TimeUnit.MILLISECONDS)));
        }

        public void shutdown() {
            scheduler.shutdownNow();
        }
    }
}
