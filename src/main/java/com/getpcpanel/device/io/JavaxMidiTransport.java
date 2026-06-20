package com.getpcpanel.device.io;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Transmitter;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.log4j.Log4j2;

/**
 * {@code javax.sound.midi}-backed {@link MidiTransport}. A thin adapter: it enumerates input devices
 * (those with at least one transmitter) and opens one, wiring a {@link Receiver} that decodes each
 * inbound {@link ShortMessage} to the raw (status, data1, data2) triple and hands it to the consumer.
 * All protocol decoding/normalization stays in {@code MidiProtocol}.
 *
 * <p><b>Native-image / graceful degradation:</b> {@code javax.sound.midi} is JNI + ServiceLoader SPI
 * ({@code com.sun.media.sound.*}); in a GraalVM native image it may enumerate nothing or throw an
 * {@code UnsatisfiedLinkError}. Every {@code MidiSystem} call here is guarded against {@link Throwable}
 * and degrades to "MIDI unavailable" (an empty device list / a failed open) rather than propagating,
 * so a broken MIDI subsystem can never crash startup or affect the PCPanel/Deej providers. The
 * {@code com.sun.media.sound.*} providers are {@code --initialize-at-run-time} and the
 * {@code javax.sound.midi.spi.*} service files are embedded as resources (see {@code pom.xml} /
 * {@code application.properties}) so the build links even though runtime enumeration is best-effort.
 */
@Log4j2
@ApplicationScoped
public class JavaxMidiTransport implements MidiTransport {
    @Override
    public List<MidiDeviceInfo> listInputs() {
        var out = new ArrayList<MidiDeviceInfo>();
        try {
            for (var info : MidiSystem.getMidiDeviceInfo()) {
                try {
                    var device = MidiSystem.getMidiDevice(info);
                    // A device that can transmit is an input source. getMaxTransmitters()==0 means
                    // it is output-only (a receiver) and is skipped (MIDI output is Phase 6).
                    if (device.getMaxTransmitters() != 0) {
                        out.add(new MidiDeviceInfo(idFor(info), info.getName()));
                    }
                } catch (Throwable t) {
                    log.debug("Skipping MIDI device {}: {}", info, t.getMessage());
                }
            }
        } catch (Throwable t) {
            // UnsatisfiedLinkError / provider failure in a native image: MIDI unavailable.
            log.warn("MIDI unavailable (cannot enumerate devices): {}", t.getMessage());
        }
        return out;
    }

    @Override
    public MidiConnection open(String id, Consumer<com.getpcpanel.device.io.MidiTransport.MidiMessage> onMessage, Consumer<Throwable> onError) {
        try {
            for (var info : MidiSystem.getMidiDeviceInfo()) {
                if (!idFor(info).equals(id)) {
                    continue;
                }
                var device = MidiSystem.getMidiDevice(info);
                if (device.getMaxTransmitters() == 0) {
                    continue;
                }
                if (!device.isOpen()) {
                    device.open();
                }
                var transmitter = device.getTransmitter();
                transmitter.setReceiver(new DecodingReceiver(onMessage));
                return new JavaxMidiConnection(id, device, transmitter);
            }
            throw new IllegalStateException("No MIDI input device with id " + id);
        } catch (Throwable t) {
            log.warn("Unable to open MIDI device {}: {}", id, t.getMessage());
            throw new IllegalStateException("Unable to open MIDI device " + id, t);
        }
    }

    /** A stable id for a MIDI device, derived from its name + vendor (the only stable identity JDK exposes). */
    private static String idFor(javax.sound.midi.MidiDevice.Info info) {
        return info.getName() + " | " + info.getVendor();
    }

    /** Decodes each inbound {@link ShortMessage} to the raw triple; everything else is dropped. */
    private static final class DecodingReceiver implements Receiver {
        private final Consumer<com.getpcpanel.device.io.MidiTransport.MidiMessage> onMessage;

        private DecodingReceiver(Consumer<com.getpcpanel.device.io.MidiTransport.MidiMessage> onMessage) {
            this.onMessage = onMessage;
        }

        @Override
        public void send(javax.sound.midi.MidiMessage message, long timeStamp) {
            // One bad message must never kill the receiver thread (mirror the HID reader guard).
            try {
                if (message instanceof ShortMessage sm) {
                    onMessage.accept(new com.getpcpanel.device.io.MidiTransport.MidiMessage(sm.getStatus(), sm.getData1(), sm.getData2()));
                }
            } catch (Throwable t) {
                // Swallow: decoding/handling is the consumer's job and it guards itself too.
            }
        }

        @Override
        public void close() {
        }
    }

    private static final class JavaxMidiConnection implements MidiConnection {
        private final String deviceId;
        private final MidiDevice device;
        private final Transmitter transmitter;
        private volatile boolean open = true;

        private JavaxMidiConnection(String deviceId, MidiDevice device, Transmitter transmitter) {
            this.deviceId = deviceId;
            this.device = device;
            this.transmitter = transmitter;
        }

        @Override
        public String deviceId() {
            return deviceId;
        }

        @Override
        public boolean isOpen() {
            return open && device.isOpen();
        }

        @Override
        public void close() {
            if (!open) {
                return;
            }
            open = false;
            try {
                transmitter.close();
            } catch (Throwable t) {
                log.debug("Error closing MIDI transmitter for {}", deviceId, t);
            }
            try {
                device.close();
            } catch (Throwable t) {
                log.debug("Error closing MIDI device {}", deviceId, t);
            }
        }
    }
}
