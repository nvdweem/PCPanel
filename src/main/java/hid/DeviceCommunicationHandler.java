package hid;

import lombok.extern.log4j.Log4j2;
import org.hid4java.HidDevice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
public class DeviceCommunicationHandler extends Thread {
    private static final byte INPUT_CODE_KNOB_CHANGE = 1;

    private static final byte INPUT_CODE_BUTTON_CHANGE = 2;

    public String key;

    public HidDevice device;

    private static final int READ_TIMEOUT_MILLIS = 100;

    private static final int PACKET_LENGTH = 64;

    private final ConcurrentLinkedQueue<byte[]> priorityQueue = new ConcurrentLinkedQueue<>();

    private final AtomicReference<byte[][]> mostRecentRGBRequest = new AtomicReference<>();

    private final Map<Integer, Long> lastButtonPress = new HashMap<>();

    public DeviceCommunicationHandler(String key, HidDevice device) {
        setName("HIDHandler");
        this.key = key;
        this.device = device;
    }

    public void addToPriorityQueue(byte[]... datas) {
        byte b;
        int i;
        byte[][] arrayOfByte;
        for (i = (arrayOfByte = datas).length, b = 0; b < i; ) {
            var d = arrayOfByte[b];
            priorityQueue.add(d);
            b++;
        }
    }

    public void addToPriorityQueue(byte[] data) {
        priorityQueue.add(data);
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
        while (DeviceScanner.CONNECTED_DEVICE_MAP.get(key) == this) {
            var moreData = true;
            while (moreData) {
                var data = new byte[64];
                var val = device.read(data, 100);
                switch (val) {
                    case -1 -> {
                        log.error("DCH ERR: {}", device.getLastErrorMessage());
                        DeviceScanner.deviceRemoved(key, device);
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
                byte b;
                int i;
                byte[][] arrayOfByte;
                for (i = (arrayOfByte = mostRecentRGBRequest.getAndSet(null)).length, b = 0; b < i; ) {
                    var data = arrayOfByte[b];
                    sendMessageReal(data);
                    b++;
                }
            }
        }
    }

    private void sendMessageReal(byte[] info) {
        if (info.length > 64)
            throw new IllegalArgumentException("info cannot be greater than packet_length");
        var message = new byte[64];
        System.arraycopy(info, 0, message, 0, info.length);
        var val = device.write(message, 64, (byte) 0);
        if (val >= 0) {
            log.debug("> [{}]", val);
        } else {
            log.error("{} {}     {}", device.getLastErrorMessage(), val, Arrays.toString(info), new Exception().fillInStackTrace());
        }
    }

    private void interpretInputData(byte[] data) {
        if (data[0] == 1) {
            var knob = data[1] & 0xFF;
            var value = data[2] & 0xFF;
            try {
                InputInterpreter.onKnobRotate(key, knob, value);
            } catch (Exception ex) {
                log.error("Unable to handle knob rotate", ex);
            }
        } else if (data[0] == 2) {
            var knob = data[1] & 0xFF;
            var value = data[2] & 0xFF;
            try {
                InputInterpreter.onButtonPress(key, knob, value == 1);
            } catch (Exception ex) {
                log.error("Unable to handle button press", ex);
            }
            lastButtonPress.put(knob, System.currentTimeMillis());
        } else {
            log.error("Invalid Input in DeviceInputListener: {}", Arrays.toString(data));
        }
    }

    public Queue<byte[]> getPriorityQueue() {
        return priorityQueue;
    }
}
