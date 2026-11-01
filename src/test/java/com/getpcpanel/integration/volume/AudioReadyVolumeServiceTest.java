package com.getpcpanel.integration.volume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.getpcpanel.commands.command.Command;
import com.getpcpanel.device.DeviceHolder;
import com.getpcpanel.integration.volume.command.CommandVolume;
import com.getpcpanel.integration.volume.platform.AudioReadyEvent;

import one.util.streamex.EntryStream;

class AudioReadyVolumeServiceTest {
    @Test
    void syncsTheVolumeDialsWhenTheAudioIsReady() {
        var triggered = new ArrayList<Object[]>();
        var sut = new AudioReadyVolumeService();
        sut.devices = new DeviceHolder() {
            @Override
            public <T extends Command> void triggerCommandsOf(Class<T> clazz, Function<EntryStream<DeviceAndDial, T>, EntryStream<DeviceAndDial, T>> chain, boolean initial) {
                triggered.add(new Object[] { clazz, initial });
            }
        };

        sut.onAudioReady(new AudioReadyEvent());

        assertEquals(1, triggered.size());
        assertEquals(CommandVolume.class, triggered.get(0)[0]);
        assertTrue((Boolean) triggered.get(0)[1], "a sync, not a turn of the dial: no overlay, no unmute");
    }
}
