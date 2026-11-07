package com.getpcpanel.integration.volume.command;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getpcpanel.commands.DialValue;
import com.getpcpanel.commands.command.DialAction.DialActionParameters;
import com.getpcpanel.integration.volume.EverythingElse;
import com.getpcpanel.integration.volume.platform.ISndCtrl;
import com.getpcpanel.integration.volume.platform.MuteType;
import com.getpcpanel.util.CdiHelper;

class CommandVolumeProcessBatchTest {
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    void expandedTargetsAreBatchedAfterAnyRequiredUnmute(boolean unmute, boolean initial) {
        var snd = mock(ISndCtrl.class);
        var groups = mock(EverythingElse.class);
        var names = List.of("Spotify", "Firefox");
        var targets = new LinkedHashSet<>(names);
        when(groups.expand(names)).thenReturn(targets);
        var command = new CommandVolumeProcess(names, "output", unmute, null);
        var dial = mock(DialValue.class);
        when(dial.getValue(command, 0, 1)).thenReturn(0.4f);
        try (var cdi = mockStatic(CdiHelper.class)) {
            cdi.when(() -> CdiHelper.getBean(ISndCtrl.class)).thenReturn(snd);
            cdi.when(() -> CdiHelper.getBean(EverythingElse.class)).thenReturn(groups);
            command.execute(new DialActionParameters("panel", initial, dial));
        }

        var ordered = inOrder(snd);
        if (unmute && !initial) {
            ordered.verify(snd).muteProcesses(targets, MuteType.unmute);
        } else {
            verify(snd, never()).muteProcesses(targets, MuteType.unmute);
        }
        ordered.verify(snd).setProcessesVolume(targets, "output", 0.4f);
        verify(snd, never()).setProcessVolume(anyString(), anyString(), anyFloat());
    }
}
