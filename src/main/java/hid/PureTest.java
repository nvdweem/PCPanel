package hid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.ScanMode;
import org.hid4java.event.HidServicesEvent;

import lombok.extern.log4j.Log4j2;
import util.Util;

@Log4j2
public class PureTest implements HidServicesListener {
    public static void main(String[] args) throws IOException {
        HidServicesSpecification hidServicesSpecification = new HidServicesSpecification();
        hidServicesSpecification.setAutoShutdown(true);
        hidServicesSpecification.setAutoStart(false);
        hidServicesSpecification.setScanInterval(3000);
        hidServicesSpecification.setPauseInterval(2000);
        hidServicesSpecification.setScanMode(ScanMode.SCAN_AT_FIXED_INTERVAL);
        HidServices hidServices = HidManager.getHidServices(hidServicesSpecification);
        log.info("Starting HID services.");
        hidServices.addHidServicesListener(new PureTest());
        hidServices.start();
        Iterator<HidDevice> iterator = hidServices.getAttachedHidDevices().iterator();
        if (iterator.hasNext()) {
            HidDevice device = iterator.next();
            log.error("{}", device);
            device.open();
            device.setNonBlocking(false);
            while (true) {
                Byte[] x = device.read();
                log.error(Arrays.toString(x));
                if (x.length == 0)
                    continue;
                log.error(x[0] + "  " + x[1] + " " + (x[2].byteValue() & 0xFF));
                if (x[0].byteValue() == 2) {
                    if (x[1].byteValue() == 2) {
                        if (x[2].byteValue() != 1) {
                            byte[] ar = {
                                    5, 5, 1, 3,
                                    -1,
                                    -1,
                                    -1,
                                    5,
                                    -1, -1,
                                    -1};
                            byte[] data = new byte[64];
                            System.arraycopy(ar, 0, data, 0, ar.length);
                            Util.printByteArray(data);
                            device.write(data, 64, (byte) 0);
                        }
                        continue;
                    }
                    if (x[1].byteValue() == 1) {
                        if (x[2].byteValue() == 1) {
                            byte[] arrayOfByte1 = new byte[64];
                            arrayOfByte1[0] = 3;
                            byte[] arrayOfByte2 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(855).array();
                            for (int j = 0; j < 4; ) {
                                arrayOfByte1[j + 1] = arrayOfByte2[j];
                                j++;
                            }
                            Util.printByteArray(arrayOfByte1);
                            device.write(arrayOfByte1, 64, (byte) 0);
                            continue;
                        }
                        byte[] data = new byte[64];
                        data[0] = 3;
                        byte[] intData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(450).array();
                        for (int i = 0; i < 4; ) {
                            data[i + 1] = intData[i];
                            i++;
                        }
                        Util.printByteArray(data);
                        device.write(data, 64, (byte) 0);
                        continue;
                    }
                    if (x[1].byteValue() == 0) {
                        if (x[2].byteValue() == 1) {
                            byte[] ar = {
                                    5,
                                    1, -1, -1,
                                    2, -1, -1,
                                    1, -1, -1,
                                    -1,
                                    3, -1, -1};
                            byte[] data = new byte[64];
                            System.arraycopy(ar, 0, data, 0, ar.length);
                            Util.printByteArray(data);
                            device.write(data, 64, (byte) 0);
                        }
                        continue;
                    }
                    if (x[1].byteValue() == 3) {
                        if (x[2].byteValue() == 1) {
                            byte[] arrayOfByte1 = {
                                    6, 2,
                                    1, -1, -1, -1,
                                    1, -1, -1, -1,
                                    1, -1, -1, -1,
                                    1, -1, -1, -1};
                            byte[] arrayOfByte2 = new byte[64];
                            System.arraycopy(arrayOfByte1, 0, arrayOfByte2, 0, arrayOfByte1.length);
                            Util.printByteArray(arrayOfByte2);
                            device.write(arrayOfByte2, 64, (byte) 0);
                            continue;
                        }
                        byte[] ar = {5, 3,

                                2, -1, -1, -1};
                        byte[] data = new byte[64];
                        System.arraycopy(ar, 0, data, 0, ar.length);
                        Util.printByteArray(data);
                        device.write(data, 64, (byte) 0);
                        continue;
                    }
                    if (x[1].byteValue() == 4) {
                        if (x[2].byteValue() == 1) {
                            byte[] arrayOfByte1 = {5, 1,
                                    -1,
                                    -1, -1,
                                    1, -1, -1,
                                    1, -1};
                            byte[] arrayOfByte2 = new byte[64];
                            System.arraycopy(arrayOfByte1, 0, arrayOfByte2, 0, arrayOfByte1.length);
                            Util.printByteArray(arrayOfByte2);
                            device.write(arrayOfByte2, 64, (byte) 0);
                            continue;
                        }
                        byte[] ar = {5, 3,
                                1, -1};
                        byte[] data = new byte[64];
                        System.arraycopy(ar, 0, data, 0, ar.length);
                        Util.printByteArray(data);
                        device.write(data, 64, (byte) 0);
                    }
                }
            }
        }
    }

    @Override
    public void hidDeviceAttached(HidServicesEvent event) {
        HidDevice device = event.getHidDevice();
        device.open();
        log.error(device.getSerialNumber());
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
    }

    @Override
    public void hidFailure(HidServicesEvent event) {
    }

    public void hidDataReceived(HidServicesEvent event) {
    }
}
