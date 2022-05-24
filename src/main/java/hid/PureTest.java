package hid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.hid4java.HidManager;
import org.hid4java.HidServicesListener;
import org.hid4java.HidServicesSpecification;
import org.hid4java.ScanMode;
import org.hid4java.event.HidServicesEvent;

import lombok.extern.log4j.Log4j2;
import util.Util;

@Log4j2
public class PureTest implements HidServicesListener {
    public static void main(String[] args) throws IOException {
        var hidServicesSpecification = new HidServicesSpecification();
        hidServicesSpecification.setAutoShutdown(true);
        hidServicesSpecification.setAutoStart(false);
        hidServicesSpecification.setScanInterval(3000);
        hidServicesSpecification.setPauseInterval(2000);
        hidServicesSpecification.setScanMode(ScanMode.SCAN_AT_FIXED_INTERVAL);
        var hidServices = HidManager.getHidServices(hidServicesSpecification);
        log.info("Starting HID services.");
        hidServices.addHidServicesListener(new PureTest());
        hidServices.start();
        var iterator = hidServices.getAttachedHidDevices().iterator();
        if (iterator.hasNext()) {
            var device = iterator.next();
            log.error("{}", device);
            device.open();
            device.setNonBlocking(false);
            while (true) {
                var x = device.read();
                log.error(Arrays.toString(x));
                if (x.length == 0)
                    continue;
                log.error("{}  {} {}", x[0], x[1], x[2] & 0xFF);
                if (x[0] == 2) {
                    if (x[1] == 2) {
                        if (x[2] != 1) {
                            byte[] ar = {
                                    5, 5, 1, 3,
                                    -1,
                                    -1,
                                    -1,
                                    5,
                                    -1, -1,
                                    -1 };
                            var data = new byte[64];
                            System.arraycopy(ar, 0, data, 0, ar.length);
                            Util.debugByteArray(data);
                            device.write(data, 64, (byte) 0);
                        }
                        continue;
                    }
                    if (x[1] == 1) {
                        if (x[2] == 1) {
                            var arrayOfByte1 = new byte[64];
                            arrayOfByte1[0] = 3;
                            var arrayOfByte2 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(855).array();
                            System.arraycopy(arrayOfByte2, 0, arrayOfByte1, 1, 4);
                            Util.debugByteArray(arrayOfByte1);
                            device.write(arrayOfByte1, 64, (byte) 0);
                            continue;
                        }
                        var data = new byte[64];
                        data[0] = 3;
                        var intData = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(450).array();
                        System.arraycopy(intData, 0, data, 1, 4);
                        Util.debugByteArray(data);
                        device.write(data, 64, (byte) 0);
                        continue;
                    }
                    if (x[1] == 0) {
                        if (x[2] == 1) {
                            byte[] ar = {
                                    5,
                                    1, -1, -1,
                                    2, -1, -1,
                                    1, -1, -1,
                                    -1,
                                    3, -1, -1 };
                            var data = new byte[64];
                            System.arraycopy(ar, 0, data, 0, ar.length);
                            Util.debugByteArray(data);
                            device.write(data, 64, (byte) 0);
                        }
                        continue;
                    }
                    if (x[1] == 3) {
                        if (x[2] == 1) {
                            byte[] arrayOfByte1 = {
                                    6, 2,
                                    1, -1, -1, -1,
                                    1, -1, -1, -1,
                                    1, -1, -1, -1,
                                    1, -1, -1, -1 };
                            var arrayOfByte2 = new byte[64];
                            System.arraycopy(arrayOfByte1, 0, arrayOfByte2, 0, arrayOfByte1.length);
                            Util.debugByteArray(arrayOfByte2);
                            device.write(arrayOfByte2, 64, (byte) 0);
                            continue;
                        }
                        byte[] ar = { 5, 3,

                                2, -1, -1, -1 };
                        var data = new byte[64];
                        System.arraycopy(ar, 0, data, 0, ar.length);
                        Util.debugByteArray(data);
                        device.write(data, 64, (byte) 0);
                        continue;
                    }
                    if (x[1] == 4) {
                        if (x[2] == 1) {
                            byte[] arrayOfByte1 = { 5, 1,
                                    -1,
                                    -1, -1,
                                    1, -1, -1,
                                    1, -1 };
                            var arrayOfByte2 = new byte[64];
                            System.arraycopy(arrayOfByte1, 0, arrayOfByte2, 0, arrayOfByte1.length);
                            Util.debugByteArray(arrayOfByte2);
                            device.write(arrayOfByte2, 64, (byte) 0);
                            continue;
                        }
                        byte[] ar = { 5, 3,
                                1, -1 };
                        var data = new byte[64];
                        System.arraycopy(ar, 0, data, 0, ar.length);
                        Util.debugByteArray(data);
                        device.write(data, 64, (byte) 0);
                    }
                }
            }
        }
    }

    @Override
    public void hidDeviceAttached(HidServicesEvent event) {
        var device = event.getHidDevice();
        device.open();
        log.error(device.getSerialNumber());
    }

    @Override
    public void hidDeviceDetached(HidServicesEvent event) {
    }

    @Override
    public void hidFailure(HidServicesEvent event) {
    }
}
