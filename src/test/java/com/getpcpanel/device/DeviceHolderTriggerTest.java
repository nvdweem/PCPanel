package com.getpcpanel.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.Commands;
import com.getpcpanel.commands.CommandsType;
import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.provider.pcpanel.DeviceCommunicationHandler.KnobRotateEvent;
import com.getpcpanel.device.provider.pcpanel.DeviceType;
import com.getpcpanel.integration.volume.command.CommandVolumeProcess;
import com.getpcpanel.profile.Profile;

import jakarta.enterprise.event.Event;

/**
 * Re-running the dials that hold a command plays each at the position the device reported. A dial that has not
 * reported yet has no position - it reads 0 - so it is left for its own first reading.
 */
class DeviceHolderTriggerTest {
    private static final String SERIAL = "serial";

    @Test
    @SuppressWarnings("unchecked")
    void skipsADialThatHasNotReportedItsPosition() {
        var profile = new Profile("p", DeviceType.PCPANEL_PRO);
        profile.setDialData(0, volume());
        profile.setDialData(4, volume());
        var device = mock(Device.class);
        when(device.getSerialNumber()).thenReturn(SERIAL);
        when(device.currentProfile()).thenReturn(profile);
        when(device.hasKnobRotation(4)).thenReturn(true);
        when(device.getKnobRotation(4)).thenReturn(200);

        var fired = new ArrayList<Object>();
        var sut = new DeviceHolder() {
            @Override
            public Collection<Device> all() {
                return List.of(device);
            }

            @Override
            public Optional<Device> getDevice(String key) {
                return Optional.of(device);
            }
        };
        sut.eventBus = mock(Event.class);
        org.mockito.Mockito.doAnswer(i -> fired.add(i.getArgument(0))).when(sut.eventBus).fire(org.mockito.ArgumentMatchers.any());

        sut.triggerCommandsOf(CommandVolumeProcess.class, Function.identity(), true);

        assertEquals(List.of(new KnobRotateEvent(SERIAL, 4, 200, true)), fired);
    }

    private static Commands volume() {
        return new Commands(List.<Command>of(new CommandVolumeProcess(List.of("System Sounds"), "", false, null)), CommandsType.allAtOnce);
    }
}
