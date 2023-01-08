package com.getpcpanel.osc;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.profile.OSCBinding;
import com.getpcpanel.profile.OSCConnectionInfo;
import com.getpcpanel.profile.SaveService;
import com.getpcpanel.util.Util;
import com.illposed.osc.OSCBadDataEvent;
import com.illposed.osc.OSCBundle;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCPacketEvent;
import com.illposed.osc.OSCPacketListener;
import com.illposed.osc.transport.OSCPortIn;
import com.illposed.osc.transport.OSCPortOut;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class OSCService {
    private final SaveService saveService;
    private OSCPortIn portIn;
    private List<OSCPortOut> ports = List.of();
    private Integer prevListenPort;
    private List<OSCConnectionInfo> prevOscConnections;
    @Getter private final Set<String> addresses = new HashSet<>();

    @EventListener(SaveService.SaveEvent.class)
    public void saveChanged() {
        log.trace("Save changed, restarting OSC");
        initSend();
        initListen();
    }

    private void initSend() {
        if (Objects.equals(prevOscConnections, saveService.get().getOscConnections()) || saveService.get().getOscConnections() == null) {
            return;
        }
        prevOscConnections = saveService.get().getOscConnections();
        ports = StreamEx.of(prevOscConnections).mapPartial(this::buildPort).toList();
    }

    private void initListen() {
        if (saveService.get().getOscListenPort() == null) {
            stopPortIn();
        }
        if (Objects.equals(prevListenPort, saveService.get().getOscListenPort())) {
            return;
        }
        prevListenPort = saveService.get().getOscListenPort();

        stopPortIn();
        try {
            portIn = new OSCPortIn(prevListenPort);
            portIn.addPacketListener(new OSCPacketListener() {
                @Override
                public void handlePacket(OSCPacketEvent event) {
                    readPacket(event.getPacket());
                }

                @Override
                public void handleBadData(OSCBadDataEvent event) {
                }
            });
            portIn.startListening();
        } catch (IOException e) {
            log.error("Unable to start OSC listener", e);
        }
    }

    private void readPacket(OSCPacket packet) {
        if (packet instanceof OSCBundle bundle) {
            bundle.getPackets().forEach(this::readPacket);
        } else if (packet instanceof OSCMessage message) {
            if (CharSequence.compare("f", message.getInfo().getArgumentTypeTags()) == 0) {
                addresses.add(message.getAddress());
            }
        }
    }

    private void stopPortIn() {
        if (portIn != null) {
            portIn.stopListening();
        }
    }

    @EventListener
    public void dialAction(DeviceCommunicationHandler.KnobRotateEvent dial) {
        if (dial.initial() || CollectionUtils.isEmpty(ports)) {
            return;
        }

        var profile = saveService.getProfile(dial.serialNum());
        var knobLength = profile.getLightingConfig().getKnobConfigs().length;
        var idx = dial.knob() < knobLength ? dial.knob() * 2 : dial.knob() + knobLength;

        var target = profile.getOscBinding().get(idx);
        if (target != null) {
            send(target, "/pcpanel/" + profile.getName() + "/knob" + dial.knob(), dial.value() / 255f);
        }
    }

    @EventListener
    public void dialAction(DeviceCommunicationHandler.ButtonPressEvent button) {
        if (CollectionUtils.isEmpty(ports)) {
            return;
        }
        var idx = button.button() * 2 + 1;

        var profile = saveService.getProfile(button.serialNum());
        var target = profile.getOscBinding().get(idx);
        if (target != null) {
            send(target, "/pcpanel/" + profile.getName() + "/button" + button.button(), button.pressed() ? 1f : 0f);
        }
    }

    private void send(@Nonnull OSCBinding target, String defaultTarget, float val) {
        var message = buildMessage(target, defaultTarget, determineValue(target, val));
        ports.forEach(port -> {
            try {
                port.send(message);
            } catch (Exception e) {
                log.error("Error sending OSC message", e);
            }
        });
    }

    private float determineValue(@Nonnull OSCBinding target, float val) {
        return Util.map(val, 0, 1, target.min(), target.max());
    }

    private static @Nonnull OSCMessage buildMessage(OSCBinding target, String defaultTarget, float val) {
        var targetString = target == null ? defaultTarget : target.address();
        try {
            return new OSCMessage(targetString, List.of(val));
        } catch (Exception e) {
            return new OSCMessage(defaultTarget, List.of(val));
        }
    }

    private Optional<OSCPortOut> buildPort(OSCConnectionInfo oscConnectionInfo) {
        try {
            return Optional.of(new OSCPortOut(InetAddress.getByName(oscConnectionInfo.host()), oscConnectionInfo.port()));
        } catch (Exception e) {
            log.error("Failed to build OSC port", e);
            return Optional.empty();
        }
    }
}
