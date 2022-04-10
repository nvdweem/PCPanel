package hid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.hid4java.HidDevice;

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
        this.key = key;
        this.device = device;
    }

    public void addToPriorityQueue(byte[]... datas) {
        byte b;
        int i;
        byte[][] arrayOfByte;
        for (i = (arrayOfByte = datas).length, b = 0; b < i; ) {
            byte[] d = arrayOfByte[b];
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
            boolean moreData = true;
            while (moreData) {
                byte[] data = new byte[64];
                int val = device.read(data, 100);
                switch (val) {
                case -1 -> {
                    System.err.println("DCH ERR: " + device.getLastErrorMessage());
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
                    byte[] data = arrayOfByte[b];
                    sendMessageReal(data);
                    b++;
                }
            }
        }
    }

    private void sendMessageReal(byte[] info) {
        if (info.length > 64)
            throw new IllegalArgumentException("info cannot be greater than packet_length");
        byte[] message = new byte[64];
        for (int i = 0; i < info.length; ) {
            message[i] = info[i];
            i++;
        }
        int val = device.write(message, 64, (byte) 0);
        if (val >= 0) {
            System.out.println("> [" + val + "]");
        } else {
            System.err.println(device.getLastErrorMessage() + " " + val + "     " + Arrays.toString(info));
            try {
                throw new Exception();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void interpretInputData(byte[] data) {
        if (data[0] == 1) {
            int knob = data[1] & 0xFF;
            int value = data[2] & 0xFF;
            try {
                InputInterpreter.onKnobRotate(key, knob, value);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else if (data[0] == 2) {
            int knob = data[1] & 0xFF;
            int value = data[2] & 0xFF;
            try {
                InputInterpreter.onButtonPress(key, knob, value == 1);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            lastButtonPress.put(Integer.valueOf(knob), Long.valueOf(System.currentTimeMillis()));
        } else {
            System.err.println("Invalid Input in DeviceInputListener: " + Arrays.toString(data));
        }
    }

    public ConcurrentLinkedQueue<byte[]> getPriorityQueue() {
        return priorityQueue;
    }
}
