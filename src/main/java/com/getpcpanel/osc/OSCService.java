package com.getpcpanel.osc;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.getpcpanel.hid.DeviceCommunicationHandler;
import com.getpcpanel.profile.OSCConnectionInfo;
import com.getpcpanel.profile.SaveService;
import com.illposed.osc.OSCMessage;
import com.illposed.osc.transport.OSCPortOut;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import one.util.streamex.StreamEx;

@Log4j2
@Service
@RequiredArgsConstructor
public class OSCService {
    private final SaveService saveService;
    private List<OSCPortOut> ports = List.of();
    private List<OSCConnectionInfo> prevOscConnections;

    @EventListener(SaveService.SaveEvent.class)
    public void saveChanged() {
        log.trace("Save changed, restarting OSC");
        init();
    }

    private void init() {
        if (Objects.equals(prevOscConnections, saveService.get().getOscConnections()) || saveService.get().getOscConnections() == null) {
            return;
        }
        prevOscConnections = saveService.get().getOscConnections();
        ports = StreamEx.of(prevOscConnections).mapPartial(this::buildPort).toList();
    }

    @EventListener
    public void dialAction(DeviceCommunicationHandler.KnobRotateEvent dial) {
        if (dial.initial()) {
            return;
        }
        var save = saveService.get().getDeviceSave(dial.serialNum());
        var knobLength = save.getLightingConfig().getKnobConfigs().length;
        var idx = dial.knob() < knobLength ? dial.knob() * 2 : dial.knob() + knobLength;

        var profile = save.getCurrentProfile();
        var target = profile.getOscBindings().get(idx);
        send(target, "/pcpanel/" + profile.getName() + "/knob" + dial.knob(), dial.value() / 255f);
    }

    @EventListener
    public void dialAction(DeviceCommunicationHandler.ButtonPressEvent button) {
        var save = saveService.get().getDeviceSave(button.serialNum());
        var idx = button.button() * 2 + 1;

        var profile = save.getCurrentProfile();
        var target = profile.getOscBindings().get(idx);
        send(target, "/pcpanel/" + profile.getName() + "/button" + button.button(), button.pressed() ? 1 : 0);
    }

    private void send(String target, String defaultTarget, Object val) {
        var message = buildMessage(target, defaultTarget, val);
        ports.forEach(port -> {
            try {
                port.send(message);
            } catch (Exception e) {
                log.error("Error sending OSC message", e);
            }
        });
    }

    private static @Nonnull OSCMessage buildMessage(String target, String defaultTarget, Object val) {
        OSCMessage message;
        try {
            message = new OSCMessage(StringUtils.defaultIfBlank(target, defaultTarget), List.of(val));
        } catch (Exception e) {
            message = new OSCMessage(defaultTarget, List.of(val));
        }
        return message;
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
