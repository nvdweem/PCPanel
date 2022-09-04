package com.getpcpanel.hid;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.hid4java.HidDevice;
import org.springframework.context.ApplicationEventPublisher;

import com.getpcpanel.device.DeviceType;
import com.getpcpanel.profile.SaveService;

import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeviceCommunicationHandler {
    private static final byte INPUT_CODE_KNOB_CHANGE = 1;
    private static final byte INPUT_CODE_BUTTON_CHANGE = 2;

    private final ApplicationEventPublisher eventPublisher;
    private final DeviceScanner deviceScanner;
    private final SaveService saveService;
    private final String key;
    private final HidDevice device;

    private static final int COM_TIMEOUT_MILLIS = 100;

    private static final int PACKET_LENGTH = 64;
    private static final int FIRST_NON_INITIAL_READS = 20; // Could have been 9 (dials/sliders of the pro) but take 20 to be safe
    private final DeviceType deviceType;
    private int readUntilNotInitial = FIRST_NON_INITIAL_READS;

    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private final KnobDebouncer debouncer = new KnobDebouncer();
    private final RollingAverageSetter rollingAverageSetter = new RollingAverageSetter();
    private final Map<Integer, Integer> prevSent = new ConcurrentHashMap<>();

    public DeviceCommunicationHandler(DeviceScanner deviceScanner, ApplicationEventPublisher eventPublisher, SaveService saveService, String key, HidDevice device, DeviceType deviceType) {
        this.eventPublisher = eventPublisher;
        this.deviceScanner = deviceScanner;
        this.saveService = saveService;
        this.key = key;
        this.device = device;
        this.deviceType = deviceType;
    }

    public void start() {
        var reader = new Thread(this::reader, "HIDReader " + device.getSerialNumber());
        var writer = new Thread(this::writer, "HIDWriter " + device.getSerialNumber());
        reader.setDaemon(true);
        writer.setDaemon(true);
        reader.start();
        writer.start();
    }

    public void sendMessage(byte[]... data) {
        Collections.addAll(queue, data);
    }

    public void reader() {
        while (isConnected()) {
            var moreData = true;
            while (moreData) {
                var data = new byte[PACKET_LENGTH];
                var val = device.read(data, COM_TIMEOUT_MILLIS);
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
                interpretInputData(readUntilNotInitial != 0, data);
            }
        }
    }

    private void writer() {
        while (isConnected()) {
            try {
                var toSend = queue.poll(COM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (toSend != null) {
                    sendMessageReal(toSend);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        debouncer.shutdown();
        rollingAverageSetter.shutdown();
    }

    private boolean isConnected() {
        //noinspection ObjectEquality
        return deviceScanner.getConnectedDevice(key) == this;
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
                triggerEvent(new ButtonPressEvent(key, knob, value == 1));
            } catch (Exception ex) {
                log.error("Unable to handle button press", ex);
            }
        } else {
            log.error("Invalid Input in DeviceInputListener: {}", Arrays.toString(data));
        }
    }

    private void triggerEvent(KnobRotateEvent o) {
        var delta = saveService.get().getSendOnlyIfDelta();
        var prevSentValue = prevSent.get(o.knob());
        var currentSendValue = o.value();
        if (prevSentValue != null && currentSendValue == prevSentValue) {
            log.trace("Prevent setting same value for {}", o);
        } else if (applyWorkaround(o.knob()) && prevSentValue != null && delta != null && prevSentValue - delta <= currentSendValue && currentSendValue <= prevSentValue + delta) {
            log.trace("Prevent setting value within delta for {}", o);
        } else {
            prevSent.put(o.knob(), currentSendValue);
            log.debug("< {}", o);
            eventPublisher.publishEvent(o);
        }
    }

    private boolean applyWorkaround(int knob) {
        return !saveService.get().isWorkaroundsOnlySliders() || knob >= deviceType.getButtonCount();
    }

    private void triggerEvent(ButtonPressEvent o) {
        log.debug("< {}", o);
        eventPublisher.publishEvent(o);
    }

    private void triggerOrDebounce(KnobRotateEvent event) {
        if (applyWorkaround(event.knob())) {
            var delay = saveService.get().getPreventSliderTwitchDelay();
            var rolling = saveService.get().getSliderRollingAverage();
            if (delay != null && delay != 0) {
                debouncer.debounce(event, delay);
            } else if (rolling != null && rolling != 0) {
                rollingAverageSetter.setKnob(event, rolling);
            } else {
                triggerEvent(event);
            }
        } else {
            triggerEvent(event);
        }
    }

    public Queue<byte[]> getQueue() {
        return queue;
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
                triggerEvent(event);
                delayedMap.put(event.knob(), Pair.of(new ArrayDeque<>(List.of(event)), null));
            } else if (prev.size() == 2) {
                // Any first event after initial or after debounce, delay this
                schedule(prev, event, delay);
                log.trace("Initial: {}", prev);
            } else if (prev.size() == 3) {
                // Second actual event after debounce, see if we are twitching
                var prevEvent = prev.removeFirst();
                if (prevEvent.value() != event.value()) { // We are not twitching
                    triggerEvent(prevEvent); // Trigger the previous
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
                    triggerEvent(event);
                } finally {
                    delayedMap.put(event.knob(), Pair.of(new ArrayDeque<>(List.of(event)), null));
                }
            }, delay, TimeUnit.MILLISECONDS)));
        }

        public void shutdown() {
            scheduler.shutdownNow();
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private class RollingAverageSetter extends Thread {
        private final Map<Integer, Deque<Pair<Long, Integer>>> targets = new ConcurrentHashMap<>();
        @Setter private Integer rollWindowMs;
        private boolean running = true;

        public RollingAverageSetter() {
            start();
        }

        public void setKnob(KnobRotateEvent knob, Integer rollWindowMs) {
            this.rollWindowMs = rollWindowMs;
            var target = targets.computeIfAbsent(knob.knob(), k -> new ArrayDeque<>());
            synchronized (target) {
                if (knob.initial()) {
                    triggerEvent(knob);
                    target.clear();
                }
                target.add(Pair.of(System.currentTimeMillis(), knob.value()));
            }
        }

        @Override
        public void run() {
            while (running) {
                synchronized (targets) {
                    targets.forEach(this::handleRoll);
                }

                try {
                    //noinspection BusyWait
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    log.error("Unable to sleep, stopping rolling average setter", e);
                    return;
                }
            }
        }

        private void handleRoll(Integer knob, Deque<Pair<Long, Integer>> timeToValue) {
            synchronized (timeToValue) {
                if (timeToValue.size() <= 1) {
                    return;
                }
                removeAllPassedButLast(timeToValue);
                var send = calcRolling(timeToValue);
                triggerEvent(new KnobRotateEvent(key, knob, send, false));
            }
        }

        private Integer calcRolling(Deque<Pair<Long, Integer>> timeToValue) {
            if (timeToValue.size() == 1) {
                return timeToValue.getFirst().getRight();
            }

            var count = 0L;
            var sum = 0d;
            var now = System.currentTimeMillis();
            var prevStamp = now - rollWindowMs;
            Integer prevValue = null;
            for (var entry : timeToValue) {
                if (prevValue != null) {
                    var weight = entry.getLeft() - prevStamp;
                    count += weight;
                    sum += weight * prevValue;

                    prevStamp = entry.getLeft();
                }

                prevValue = entry.getRight();
            }

            if (prevValue != null) {
                var weight = now - prevStamp;
                count += weight;
                sum += weight * prevValue;
            }

            //noinspection NumericCastThatLosesPrecision
            return (int) (sum / count);
        }

        private void removeAllPassedButLast(Deque<Pair<Long, Integer>> timeToValue) {
            var now = System.currentTimeMillis();
            while (timeToValue.size() > 1) {
                var first = timeToValue.removeFirst();
                var second = timeToValue.getFirst();
                if (second.getLeft() >= now - rollWindowMs) {
                    timeToValue.addFirst(first);
                    return;
                }
            }
        }

        public void shutdown() {
            running = false;
        }
    }
}
