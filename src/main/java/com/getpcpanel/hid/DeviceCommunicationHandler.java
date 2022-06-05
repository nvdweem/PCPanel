package com.getpcpanel.hid;

import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.hid4java.HidDevice;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeviceCommunicationHandler extends Thread {
    private static final byte INPUT_CODE_KNOB_CHANGE = 1;

    private static final byte INPUT_CODE_BUTTON_CHANGE = 2;

    private final DeviceScanner deviceScanner;
    private final InputInterpreter inputInterpreter;
    private final String key;
    private final HidDevice device;

    private static final int READ_TIMEOUT_MILLIS = 100;

    private static final int PACKET_LENGTH = 64;

    private final ConcurrentLinkedQueue<byte[]> priorityQueue = new ConcurrentLinkedQueue<>();

    private final AtomicReference<byte[][]> mostRecentRGBRequest = new AtomicReference<>();

    public DeviceCommunicationHandler(DeviceScanner deviceScanner, InputInterpreter inputInterpreter, String key, HidDevice device) {
        this.inputInterpreter = inputInterpreter;
        setName("HIDHandler");
        this.deviceScanner = deviceScanner;
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
                interpretInputData(data);
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
    }

    private void sendMessageReal(byte[] info) {
        if (info.length > PACKET_LENGTH)
            throw new IllegalArgumentException("info cannot be greater than packet_length");
        var message = new byte[PACKET_LENGTH];
        System.arraycopy(info, 0, message, 0, info.length);
        var val = device.write(message, PACKET_LENGTH, (byte) 0);
        if (val >= 0) {
            log.trace("> [{}]", val);
        } else {
            log.error("{} {}     {}", device.getLastErrorMessage(), val, Arrays.toString(info), new Exception().fillInStackTrace());
        }
    }

    private void interpretInputData(byte[] data) {
        if (data[0] == INPUT_CODE_KNOB_CHANGE) {
            var knob = data[1] & 0xFF;
            var value = data[2] & 0xFF;
            try {
                inputInterpreter.onKnobRotate(key, knob, value);
            } catch (Exception ex) {
                log.error("Unable to handle knob rotate", ex);
            }
        } else if (data[0] == INPUT_CODE_BUTTON_CHANGE) {
            var knob = data[1] & 0xFF;
            var value = data[2] & 0xFF;
            try {
                inputInterpreter.onButtonPress(key, knob, value == 1);
            } catch (Exception ex) {
                log.error("Unable to handle button press", ex);
            }
        } else {
            log.error("Invalid Input in DeviceInputListener: {}", Arrays.toString(data));
        }
    }

    public Queue<byte[]> getPriorityQueue() {
        return priorityQueue;
    }
}
